package com.tss.aml.services;

import com.tss.aml.dtos.batch.BatchUploadResponseDto;
import com.tss.aml.dtos.batch.BatchValidationErrorDto;
import com.tss.aml.dtos.batch.BatchValidationResult;
import com.tss.aml.dtos.batch.ParsedTransactionRowDto;
import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.system.Users;
import com.tss.aml.entities.tenant.Account;
import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.entities.tenant.BatchValidationError;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.entities.tenant.TransactionBatch;
import com.tss.aml.enums.BatchStatus;
import com.tss.aml.enums.RuleStatus;
import com.tss.aml.repositories.AccountRepository;
import com.tss.aml.repositories.BatchValidationErrorRepository;
import com.tss.aml.repositories.FinancialTransactionRepository;
import com.tss.aml.repositories.RuleRepository;
import com.tss.aml.repositories.TransactionBatchRepository;
import com.tss.aml.repositories.UserRepository;
import com.tss.aml.ruleengine.RuleEngineService;
import com.tss.aml.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BatchIngestionService {

    private final BatchValidationService validationService;
    private final TransactionBatchRepository batchRepository;
    private final BatchValidationErrorRepository errorRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final RuleRepository ruleRepository;
    private final UserRepository userRepository;
    private final RuleEngineService ruleEngineService;

    @Transactional
    public BatchUploadResponseDto processBatchUpload(MultipartFile file, CustomUserDetails currentUser) {
        if (currentUser == null || currentUser.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User authentication principal missing");
        }

        Users uploadingUser = userRepository.findById(currentUser.getUserId())
                .orElseGet(() -> userRepository.findByEmail(currentUser.getUsername())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found: " + currentUser.getUsername())));


        String fileName = file != null ? file.getOriginalFilename() : "unknown.xlsx";
        String batchCode = "BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        BatchValidationResult<ParsedTransactionRowDto> validationResult = validationService.validateExcelBatch(file);

        if (!validationResult.isValid()) {
            TransactionBatch rejectedBatch = TransactionBatch.builder()
                    .batchCode(batchCode)
                    .fileReference(fileName != null ? fileName : "unknown.xlsx")
                    .uploadedBy(uploadingUser)
                    .status(BatchStatus.REJECTED)
                    .totalRecords(validationResult.getParsedData().size())
                    .alertsGeneratedCount(validationResult.getErrors().size())
                    .uploadedAt(LocalDateTime.now())
                    .build();

            List<BatchValidationError> errorEntities = validationResult.getErrors().stream()
                    .map(err -> BatchValidationError.builder()
                            .batch(rejectedBatch)
                            .rowNumber(err.getRowNumber())
                            .fieldName(err.getFieldName())
                            .errorMessage(err.getErrorMessage())
                            .createdAt(LocalDateTime.now())
                            .build())
                    .toList();

            rejectedBatch.setValidationErrors(errorEntities);
            TransactionBatch savedRejected = batchRepository.save(rejectedBatch);

            return BatchUploadResponseDto.builder()
                    .batchId(savedRejected.getBatchId())
                    .batchCode(savedRejected.getBatchCode())
                    .status(BatchStatus.REJECTED)
                    .totalRecords(validationResult.getParsedData().size())
                    .alertsGeneratedCount(validationResult.getErrors().size())
                    .uploadedAt(savedRejected.getUploadedAt())
                    .errors(validationResult.getErrors())
                    .build();
        }

        List<ParsedTransactionRowDto> parsedRows = validationResult.getParsedData();

        TransactionBatch batch = TransactionBatch.builder()
                .batchCode(batchCode)
                .fileReference(fileName != null ? fileName : "uploaded_batch.xlsx")
                .uploadedBy(uploadingUser)
                .status(BatchStatus.QUEUED)
                .totalRecords(parsedRows.size())
                .alertsGeneratedCount(validationResult.getErrors().size())
                .uploadedAt(LocalDateTime.now())
                .build();

        TransactionBatch savedBatch = batchRepository.save(batch);

        List<FinancialTransaction> txnsToSave = new ArrayList<>();
        for (ParsedTransactionRowDto row : parsedRows) {
            Account account = accountRepository.findByAccountNumber(row.getOriginatorAccountNo())
                    .orElseGet(() -> accountRepository.save(Account.builder()
                            .accountNumber(row.getOriginatorAccountNo())
                            .accountHolderName(row.getOriginatorName())
                            .countryCode(row.getCountryCode())
                            .build()));

            FinancialTransaction txn = FinancialTransaction.builder()
                    .batch(savedBatch)
                    .txnNo(row.getTxnNo())
                    .originatorAccount(account)
                    .amount(row.getAmount())
                    .currency(row.getCurrency())
                    .txnType(row.getTxnType())
                    .direction(row.getDirection())
                    .counterpartyName(row.getCounterpartyName())
                    .counterpartyAccountNo(row.getCounterpartyAccountNo())
                    .counterpartyBank(row.getCounterpartyBank())
                    .counterpartyCountryCode(row.getCounterpartyCountryCode())
                    .txnTimestamp(row.getTxnTimestamp())
                    .countryCode(row.getCountryCode())
                    .build();

            txnsToSave.add(txn);
        }

        List<FinancialTransaction> savedTxns = transactionRepository.saveAll(txnsToSave);

        // Fetch active rules assigned to the uploading user's tenant
        List<Rule> activeRules = Collections.emptyList();
        if (uploadingUser != null && uploadingUser.getTenant() != null) {
            activeRules = ruleRepository.findActiveRulesByTenantId(uploadingUser.getTenant().getTenantId());
        }
        if (activeRules.isEmpty()) {
            activeRules = ruleRepository.findByStatus(RuleStatus.ACTIVE);
        }

        List<Alert> generatedAlerts = ruleEngineService.evaluateBatch(savedTxns, activeRules);

        int alertsCount = generatedAlerts != null ? generatedAlerts.size() : 0;
        BatchStatus finalStatus = alertsCount > 0 ? BatchStatus.PROCESSED_ALERTS_GENERATED : BatchStatus.PROCESSED_NO_ALERTS;

        savedBatch.setAlertsGeneratedCount(alertsCount);
        savedBatch.setStatus(finalStatus);
        savedBatch.setProcessedAt(LocalDateTime.now());

        TransactionBatch updatedBatch = batchRepository.save(savedBatch);

        return BatchUploadResponseDto.builder()
                .batchId(updatedBatch.getBatchId())
                .batchCode(updatedBatch.getBatchCode())
                .status(finalStatus)
                .totalRecords(parsedRows.size())
                .alertsGeneratedCount(alertsCount)
                .uploadedAt(updatedBatch.getUploadedAt())
                .errors(Collections.emptyList())
                .build();
    }

    @Transactional(readOnly = true)
    public BatchUploadResponseDto getBatchDetails(UUID batchId, CustomUserDetails currentUser) {
        TransactionBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found with ID: " + batchId));

        if (currentUser != null && currentUser.getTenantId() != null &&
                batch.getUploadedBy() != null && batch.getUploadedBy().getTenant() != null) {
            if (!batch.getUploadedBy().getTenant().getTenantId().equals(currentUser.getTenantId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Cannot access batch belonging to another tenant.");
            }
        }

        List<BatchValidationErrorDto> errorDtos = Collections.emptyList();

        if (batch.getStatus() == BatchStatus.REJECTED && batch.getValidationErrors() != null) {
            errorDtos = batch.getValidationErrors().stream()
                    .map(err -> BatchValidationErrorDto.builder()
                            .rowNumber(err.getRowNumber())
                            .fieldName(err.getFieldName())
                            .errorMessage(err.getErrorMessage())
                            .build())
                    .toList();
        }

        return BatchUploadResponseDto.builder()
                .batchId(batch.getBatchId())
                .batchCode(batch.getBatchCode())
                .status(batch.getStatus())
                .totalRecords(batch.getTotalRecords())
                .alertsGeneratedCount(batch.getAlertsGeneratedCount())
                .uploadedAt(batch.getUploadedAt())
                .errors(errorDtos)
                .build();
    }
}
