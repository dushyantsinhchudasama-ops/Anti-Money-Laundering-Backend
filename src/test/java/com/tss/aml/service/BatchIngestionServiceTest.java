package com.tss.aml.service;

import com.tss.aml.dtos.batch.BatchUploadResponseDto;
import com.tss.aml.dtos.batch.BatchValidationErrorDto;
import com.tss.aml.dtos.batch.BatchValidationResult;
import com.tss.aml.dtos.batch.ParsedTransactionRowDto;
import com.tss.aml.entities.system.Users;
import com.tss.aml.entities.tenant.TransactionBatch;
import com.tss.aml.enums.BatchStatus;
import com.tss.aml.enums.TransactionDirection;
import com.tss.aml.enums.TransactionType;
import com.tss.aml.repositories.*;
import com.tss.aml.ruleengine.RuleEngineService;
import com.tss.aml.services.implementation.BatchIngestionServiceImpl;
import com.tss.aml.services.BatchValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BatchIngestionServiceTest {

    private BatchIngestionServiceImpl ingestionService;
    private BatchValidationService validationService;
    private TransactionBatchRepository batchRepository;
    private BatchValidationErrorRepository errorRepository;
    private FinancialTransactionRepository transactionRepository;
    private AccountRepository accountRepository;
    private RuleRepository ruleRepository;
    private RuleEngineService ruleEngineService;

    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        validationService = Mockito.mock(BatchValidationService.class);
        batchRepository = Mockito.mock(TransactionBatchRepository.class);
        errorRepository = Mockito.mock(BatchValidationErrorRepository.class);
        transactionRepository = Mockito.mock(FinancialTransactionRepository.class);
        accountRepository = Mockito.mock(AccountRepository.class);
        ruleRepository = Mockito.mock(RuleRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        ruleEngineService = Mockito.mock(RuleEngineService.class);

        ingestionService = new BatchIngestionServiceImpl(
                validationService,
                batchRepository,
                errorRepository,
                transactionRepository,
                accountRepository,
                ruleRepository,
                userRepository,
                ruleEngineService
        );

        when(batchRepository.save(any(TransactionBatch.class))).thenAnswer(invocation -> {
            TransactionBatch b = invocation.getArgument(0);
            if (b.getBatchId() == null) {
                b.setBatchId(UUID.randomUUID());
            }
            return b;
        });

        when(accountRepository.findByAccountNumber(any())).thenReturn(Optional.empty());
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void testRejectedBatch_enforcesAllOrNothingPolicy_andDoesNotSaveTransactions() {
        MockMultipartFile file = new MockMultipartFile("file", "invalid.xlsx", "text/plain", "data".getBytes());
        Users user = Users.builder().userId(UUID.randomUUID()).email("admin@bank.com").build();

        BatchValidationErrorDto error = new BatchValidationErrorDto(2, "Amount", "Amount must be greater than zero.");
        BatchValidationResult<ParsedTransactionRowDto> invalidResult = BatchValidationResult.<ParsedTransactionRowDto>builder()
                .valid(false)
                .errors(List.of(error))
                .parsedData(Collections.emptyList())
                .build();

        when(validationService.validateExcelBatch(file)).thenReturn(invalidResult);

        BatchUploadResponseDto response = ingestionService.processBatchUpload(file, user);

        assertEquals(BatchStatus.REJECTED, response.getStatus(), "Batch status must be REJECTED when validation fails");
        assertEquals(0, response.getTotalRecords());
        assertEquals(1, response.getErrors().size());
        assertEquals("Amount", response.getErrors().get(0).getFieldName());

        // Verify zero transactions saved
        verify(transactionRepository, never()).saveAll(any());
        // Verify rule engine not executed
        verify(ruleEngineService, never()).evaluateBatch(any(), any());
    }

    @Test
    void testSuccessfulBatchUpload_runsRuleEngine_andUpdatesStatus() {
        MockMultipartFile file = new MockMultipartFile("file", "valid.xlsx", "text/plain", "data".getBytes());
        Users user = Users.builder().userId(UUID.randomUUID()).email("admin@bank.com").build();

        ParsedTransactionRowDto row = ParsedTransactionRowDto.builder()
                .txnNo("TXN-100")
                .originatorAccountNo("ACC-500")
                .originatorName("Alice")
                .amount(new BigDecimal("50000.00"))
                .currency("USD")
                .txnType(TransactionType.NEFT)
                .direction(TransactionDirection.OUT)
                .txnTimestamp(LocalDateTime.now())
                .countryCode("US")
                .build();

        BatchValidationResult<ParsedTransactionRowDto> validResult = BatchValidationResult.<ParsedTransactionRowDto>builder()
                .valid(true)
                .errors(Collections.emptyList())
                .parsedData(List.of(row))
                .build();

        when(validationService.validateExcelBatch(file)).thenReturn(validResult);
        when(transactionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ruleRepository.findByStatus(any())).thenReturn(Collections.emptyList());
        when(ruleEngineService.evaluateBatch(any(), any())).thenReturn(Collections.emptyList());

        BatchUploadResponseDto response = ingestionService.processBatchUpload(file, user);

        assertEquals(BatchStatus.PROCESSED_NO_ALERTS, response.getStatus());
        assertEquals(1, response.getTotalRecords());
        assertEquals(0, response.getAlertsGeneratedCount());
        assertTrue(response.getErrors().isEmpty());

        verify(transactionRepository).saveAll(any());
        verify(ruleEngineService).evaluateBatch(any(), any());
    }
}
