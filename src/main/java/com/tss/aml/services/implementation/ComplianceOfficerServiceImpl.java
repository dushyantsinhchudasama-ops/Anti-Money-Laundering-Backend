package com.tss.aml.services.implementation;

import com.tss.aml.dtos.tenant.AlertDetailResponse;
import com.tss.aml.dtos.tenant.AlertResponse;
import com.tss.aml.dtos.tenant.CaseResponse;
import com.tss.aml.dtos.tenant.ComplianceOfficerDashboardResponse;
import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.system.Users;
import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.entities.tenant.AmlCase;
import com.tss.aml.entities.tenant.AuditLog;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.AlertStatus;
import com.tss.aml.enums.CaseStatus;
import com.tss.aml.exceptions.base.ResourceNotFoundException;
import com.tss.aml.repositories.AlertRepository;
import com.tss.aml.repositories.AmlCaseRepository;
import com.tss.aml.repositories.AuditLogRepository;
import com.tss.aml.repositories.UserRepository;
import com.tss.aml.security.CustomUserDetails;
import com.tss.aml.services.interfaces.ComplianceOfficerService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.tss.aml.dtos.tenant.CaseNoteCreateRequest;
import com.tss.aml.dtos.tenant.CaseNoteResponse;
import com.tss.aml.entities.tenant.CaseNote;
import com.tss.aml.repositories.CaseNoteRepository;
import com.tss.aml.dtos.tenant.CaseInvestigationResponse;
import com.tss.aml.repositories.AccountRepository;
import com.tss.aml.repositories.FinancialTransactionRepository;
import java.util.Map;
import com.tss.aml.entities.tenant.Account;
import com.tss.aml.entities.tenant.SarStr;
import com.tss.aml.enums.FiuTypologyCategory;
import com.tss.aml.enums.SarStrType;
import com.tss.aml.repositories.SarStrRepository;
import com.tss.aml.dtos.tenant.SarStrFilingRequest;
import com.tss.aml.dtos.tenant.SarStrPreviewResponse;
import com.tss.aml.dtos.tenant.SarStrResponse;
import com.tss.aml.utils.SarStrPdfGenerator;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceOfficerServiceImpl implements ComplianceOfficerService {

    private final AmlCaseRepository amlCaseRepository;
    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final CaseNoteRepository caseNoteRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final AccountRepository accountRepository;
    private final SarStrRepository sarStrRepository;

    @Override
    @Transactional(readOnly = true)
    public ComplianceOfficerDashboardResponse getDashboard(CustomUserDetails currentUser) {
        UUID coId = currentUser.getUserId();

        long totalAssignedCases = amlCaseRepository.countByAssignedTo_UserId(coId);
        long openCasesCount = amlCaseRepository.countByAssignedTo_UserIdAndStatus(coId, CaseStatus.OPEN);
        long inProgressCasesCount = amlCaseRepository.countByAssignedTo_UserIdAndStatus(coId, CaseStatus.IN_PROGRESS);
        long escalatedCasesCount = amlCaseRepository.countByAssignedTo_UserIdAndStatus(coId, CaseStatus.ESCALATED);
        long closedSarCount = amlCaseRepository.countByAssignedTo_UserIdAndStatus(coId, CaseStatus.CLOSED_SAR_FILED);
        long closedNoActionCount = amlCaseRepository.countByAssignedTo_UserIdAndStatus(coId, CaseStatus.CLOSED_NO_ACTION);
        long relatedAlertsCount = alertRepository.countByAmlCase_AssignedTo_UserId(coId);

        return ComplianceOfficerDashboardResponse.builder()
                .totalAssignedCases(totalAssignedCases)
                .openCasesCount(openCasesCount)
                .inProgressCasesCount(inProgressCasesCount)
                .escalatedCasesCount(escalatedCasesCount)
                .closedCasesCount(closedSarCount + closedNoActionCount)
                .relatedAlertsCount(relatedAlertsCount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CaseResponse> getAssignedCases(CaseStatus status, Pageable pageable, CustomUserDetails currentUser) {
        Page<AmlCase> casesPage = amlCaseRepository.findCasesWithFilters(status, currentUser.getUserId(), pageable);
        return casesPage.map(this::mapToCaseResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CaseResponse getCaseDetail(UUID caseId, CustomUserDetails currentUser) {
        AmlCase amlCase = amlCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case with ID " + caseId + " not found"));

        if (amlCase.getAssignedTo() == null || !amlCase.getAssignedTo().getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Access denied: Case is not assigned to you.");
        }

        return mapToCaseResponse(amlCase);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AlertResponse> getAssignedAlerts(
            AlertSeverity severity,
            UUID ruleId,
            AlertStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable,
            CustomUserDetails currentUser
    ) {
        Specification<Alert> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("amlCase").get("assignedTo").get("userId"), currentUser.getUserId()));

            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (ruleId != null) {
                predicates.add(cb.equal(root.get("rule").get("ruleId"), ruleId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("alertStatus"), status));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Alert> alertsPage = alertRepository.findAll(spec, pageable);
        return alertsPage.map(this::mapToAlertResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AlertDetailResponse getAlertDetail(UUID alertId, CustomUserDetails currentUser) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert with ID " + alertId + " not found"));

        if (alert.getAmlCase() == null || alert.getAmlCase().getAssignedTo() == null
                || !alert.getAmlCase().getAssignedTo().getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Access denied: Alert is not assigned to you.");
        }

        return mapToAlertDetailResponse(alert);
    }

    @Override
    @Transactional
    public CaseResponse startInvestigation(UUID caseId, CustomUserDetails currentUser) {
        AmlCase amlCase = amlCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case with ID " + caseId + " not found"));

        if (amlCase.getAssignedTo() == null || !amlCase.getAssignedTo().getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Access denied: Case is not assigned to you.");
        }

        if (amlCase.getStatus() == CaseStatus.IN_PROGRESS) {
            throw new IllegalStateException("Case is already IN_PROGRESS");
        }

        if (amlCase.getStatus() != CaseStatus.OPEN) {
            throw new IllegalStateException("Cannot start investigation for case with status: " + amlCase.getStatus());
        }

        amlCase.setStatus(CaseStatus.IN_PROGRESS);
        amlCaseRepository.save(amlCase);

        Users currentUserEntity = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in user not found: " + currentUser.getUserId()));

        AuditLog auditLog = AuditLog.builder()
                .actor(currentUserEntity)
                .action("CASE_INVESTIGATION_STARTED")
                .entityType("CASE")
                .entityId(amlCase.getCaseId().toString())
                .details("Compliance Officer " + currentUserEntity.getEmail() + " started investigation on case " + amlCase.getCaseCode())
                .build();
        auditLogRepository.save(auditLog);

        log.info("Compliance Officer '{}' (ID: {}) started investigation on Case '{}' (ID: {})",
                currentUserEntity.getEmail(), currentUserEntity.getUserId(), amlCase.getCaseCode(), amlCase.getCaseId());

        return mapToCaseResponse(amlCase);
    }

    private CaseResponse mapToCaseResponse(AmlCase amlCase) {
        List<AlertResponse> alertResponses = amlCase.getAlerts() != null ?
                amlCase.getAlerts().stream().map(this::mapToAlertResponse).collect(Collectors.toList()) : List.of();

        return CaseResponse.builder()
                .caseId(amlCase.getCaseId())
                .caseCode(amlCase.getCaseCode())
                .status(amlCase.getStatus())
                .createdById(amlCase.getCreatedBy() != null ? amlCase.getCreatedBy().getUserId() : null)
                .createdByName(amlCase.getCreatedBy() != null ? amlCase.getCreatedBy().getFirstName() + " " + amlCase.getCreatedBy().getLastName() : null)
                .assignedToId(amlCase.getAssignedTo() != null ? amlCase.getAssignedTo().getUserId() : null)
                .assignedToName(amlCase.getAssignedTo() != null ? amlCase.getAssignedTo().getFirstName() + " " + amlCase.getAssignedTo().getLastName() : null)
                .alertCount(alertResponses.size())
                .alerts(alertResponses)
                .createdAt(amlCase.getCreatedAt())
                .closedAt(amlCase.getClosedAt())
                .falsePositiveRationale(amlCase.getFalsePositiveRationale())
                .build();
    }

    private AlertResponse mapToAlertResponse(Alert alert) {
        return AlertResponse.builder()
                .alertId(alert.getAlertId())
                .alertCode(alert.getAlertCode())
                .transactionId(alert.getTransaction() != null ? alert.getTransaction().getTransactionId() : null)
                .transactionTxnNo(alert.getTransaction() != null ? alert.getTransaction().getTxnNo() : null)
                .ruleId(alert.getRule() != null ? alert.getRule().getRuleId() : null)
                .ruleCode(alert.getRule() != null ? alert.getRule().getRuleCode() : null)
                .ruleName(alert.getRule() != null ? alert.getRule().getRuleName() : null)
                .severity(alert.getSeverity())
                .alertStatus(alert.getAlertStatus())
                .caseId(alert.getAmlCase() != null ? alert.getAmlCase().getCaseId() : null)
                .caseCode(alert.getAmlCase() != null ? alert.getAmlCase().getCaseCode() : null)
                .createdAt(alert.getCreatedAt())
                .build();
    }

    private AlertDetailResponse mapToAlertDetailResponse(Alert alert) {
        FinancialTransaction txn = alert.getTransaction();
        Rule rule = alert.getRule();

        return AlertDetailResponse.builder()
                .alertId(alert.getAlertId())
                .alertCode(alert.getAlertCode())
                .severity(alert.getSeverity())
                .alertStatus(alert.getAlertStatus())
                .createdAt(alert.getCreatedAt())
                .transactionId(txn != null ? txn.getTransactionId() : null)
                .transactionTxnNo(txn != null ? txn.getTxnNo() : null)
                .amount(txn != null ? txn.getAmount() : null)
                .currency(txn != null ? txn.getCurrency() : null)
                .transactionType(txn != null ? txn.getTxnType() : null)
                .direction(txn != null ? txn.getDirection() : null)
                .originatorAccountNumber(txn != null && txn.getOriginatorAccount() != null ? txn.getOriginatorAccount().getAccountNumber() : null)
                .counterpartyName(txn != null ? txn.getCounterpartyName() : null)
                .counterpartyAccountNo(txn != null ? txn.getCounterpartyAccountNo() : null)
                .counterpartyBank(txn != null ? txn.getCounterpartyBank() : null)
                .counterpartyCountryCode(txn != null ? txn.getCounterpartyCountryCode() : null)
                .transactionTimestamp(txn != null ? txn.getTxnTimestamp() : null)
                .ruleId(rule != null ? rule.getRuleId() : null)
                .ruleCode(rule != null ? rule.getRuleCode() : null)
                .ruleName(rule != null ? rule.getRuleName() : null)
                .ruleDescription(rule != null ? rule.getDescription() : null)
                .typology(rule != null ? rule.getTypology() : null)
                .ruleParameters(rule != null ? rule.getParameters() : null)
                .caseId(alert.getAmlCase() != null ? alert.getAmlCase().getCaseId() : null)
                .caseCode(alert.getAmlCase() != null ? alert.getAmlCase().getCaseCode() : null)
                .build();
    }

    @Override
    @Transactional
    public CaseNoteResponse createCaseNote(UUID caseId, CaseNoteCreateRequest request, CustomUserDetails currentUser) {
        AmlCase amlCase = amlCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case with ID " + caseId + " not found"));

        if (amlCase.getAssignedTo() == null || !amlCase.getAssignedTo().getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Access denied: Case is not assigned to you.");
        }

        if (amlCase.getStatus() == CaseStatus.CLOSED_NO_ACTION || amlCase.getStatus() == CaseStatus.CLOSED_SAR_FILED) {
            throw new IllegalStateException("Cannot add note to closed case with status: " + amlCase.getStatus());
        }

        if (request == null || request.getNoteType() == null) {
            throw new IllegalArgumentException("Note type must be provided");
        }
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Note content must not be blank");
        }

        Users currentUserEntity = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in user not found: " + currentUser.getUserId()));

        if (amlCase.getStatus() == CaseStatus.OPEN) {
            amlCase.setStatus(CaseStatus.IN_PROGRESS);
            amlCaseRepository.save(amlCase);

            AuditLog startAudit = AuditLog.builder()
                    .actor(currentUserEntity)
                    .action("CASE_INVESTIGATION_STARTED")
                    .entityType("CASE")
                    .entityId(amlCase.getCaseId().toString())
                    .details("Compliance Officer " + currentUserEntity.getEmail() + " started investigation on case " + amlCase.getCaseCode())
                    .build();
            auditLogRepository.save(startAudit);
        }

        CaseNote caseNote = CaseNote.builder()
                .amlCase(amlCase)
                .author(currentUserEntity)
                .noteType(request.getNoteType())
                .content(request.getContent().trim())
                .createdAt(LocalDateTime.now())
                .build();
        caseNoteRepository.save(caseNote);

        AuditLog noteAudit = AuditLog.builder()
                .actor(currentUserEntity)
                .action("CASE_NOTE_ADDED")
                .entityType("CASE")
                .entityId(amlCase.getCaseId().toString())
                .details("Added note (" + caseNote.getNoteType() + ") to case " + amlCase.getCaseCode() + ": " + caseNote.getNoteId())
                .build();
        auditLogRepository.save(noteAudit);

        log.info("Compliance Officer '{}' added note '{}' to Case '{}'",
                currentUserEntity.getEmail(), caseNote.getNoteId(), amlCase.getCaseCode());

        return mapToCaseNoteResponse(caseNote);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaseNoteResponse> getCaseNotes(UUID caseId, CustomUserDetails currentUser) {
        AmlCase amlCase = amlCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case with ID " + caseId + " not found"));

        if (amlCase.getAssignedTo() == null || !amlCase.getAssignedTo().getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Access denied: Case is not assigned to you.");
        }

        List<CaseNote> notes = caseNoteRepository.findByAmlCase_CaseIdOrderByCreatedAtAsc(caseId);
        return notes.stream().map(this::mapToCaseNoteResponse).collect(Collectors.toList());
    }

    private CaseNoteResponse mapToCaseNoteResponse(CaseNote note) {
        Users author = note.getAuthor();
        String authorName = author != null ? (author.getFirstName() + " " + author.getLastName()).trim() : null;
        String authorEmail = author != null ? author.getEmail() : null;
        UUID authorId = author != null ? author.getUserId() : null;

        return CaseNoteResponse.builder()
                .noteId(note.getNoteId())
                .noteType(note.getNoteType())
                .content(note.getContent())
                .authorId(authorId)
                .authorName(authorName)
                .authorEmail(authorEmail)
                .createdAt(note.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CaseInvestigationResponse getCaseInvestigationData(UUID caseId, org.springframework.data.domain.Pageable pageable, CustomUserDetails currentUser) {
        AmlCase amlCase = amlCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case with ID " + caseId + " not found"));

        if (amlCase.getAssignedTo() == null || !amlCase.getAssignedTo().getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Access denied: Case is not assigned to you.");
        }

        // 1. Case Summary
        Users assignedTo = amlCase.getAssignedTo();
        Users createdBy = amlCase.getCreatedBy();
        CaseInvestigationResponse.CaseSummaryDto caseSummary = CaseInvestigationResponse.CaseSummaryDto.builder()
                .caseId(amlCase.getCaseId())
                .caseCode(amlCase.getCaseCode())
                .status(amlCase.getStatus())
                .assignedToId(assignedTo != null ? assignedTo.getUserId() : null)
                .assignedToName(assignedTo != null ? (assignedTo.getFirstName() + " " + assignedTo.getLastName()).trim() : null)
                .assignedToEmail(assignedTo != null ? assignedTo.getEmail() : null)
                .createdById(createdBy != null ? createdBy.getUserId() : null)
                .createdByName(createdBy != null ? (createdBy.getFirstName() + " " + createdBy.getLastName()).trim() : null)
                .falsePositiveRationale(amlCase.getFalsePositiveRationale())
                .createdAt(amlCase.getCreatedAt())
                .closedAt(amlCase.getClosedAt())
                .build();

        // 2. Triggering Transactions & Primary Account/Batch
        List<CaseInvestigationResponse.TriggeringTransactionDto> triggeringTxns = new ArrayList<>();
        List<UUID> triggeringTxnIds = new ArrayList<>();
        Account primaryAccount = null;
        UUID primaryBatchId = null;

        if (amlCase.getAlerts() != null && !amlCase.getAlerts().isEmpty()) {
            for (Alert alert : amlCase.getAlerts()) {
                FinancialTransaction ft = alert.getTransaction();
                if (ft != null) {
                    triggeringTxnIds.add(ft.getTransactionId());
                    if (primaryAccount == null) {
                        primaryAccount = ft.getOriginatorAccount();
                    }
                    if (primaryBatchId == null && ft.getBatch() != null) {
                        primaryBatchId = ft.getBatch().getBatchId();
                    }
                    Rule r = alert.getRule();
                    triggeringTxns.add(CaseInvestigationResponse.TriggeringTransactionDto.builder()
                            .transactionId(ft.getTransactionId())
                            .txnNo(ft.getTxnNo())
                            .amount(ft.getAmount())
                            .currency(ft.getCurrency())
                            .txnType(ft.getTxnType())
                            .direction(ft.getDirection())
                            .counterpartyName(ft.getCounterpartyName())
                            .counterpartyAccountNo(ft.getCounterpartyAccountNo())
                            .counterpartyBank(ft.getCounterpartyBank())
                            .counterpartyCountryCode(ft.getCounterpartyCountryCode())
                            .txnTimestamp(ft.getTxnTimestamp())
                            .countryCode(ft.getCountryCode())
                            .batchId(ft.getBatch() != null ? ft.getBatch().getBatchId() : null)
                            .batchCode(ft.getBatch() != null ? ft.getBatch().getBatchCode() : null)
                            .alertCode(alert.getAlertCode())
                            .alertSeverity(alert.getSeverity())
                            .ruleCode(r != null ? r.getRuleCode() : null)
                            .ruleName(r != null ? r.getRuleName() : null)
                            .build());
                }
            }
        }

        // 3. Customer / Account Profile
        CaseInvestigationResponse.CustomerAccountProfileDto customerProfile = null;
        if (primaryAccount != null) {
            customerProfile = CaseInvestigationResponse.CustomerAccountProfileDto.builder()
                    .accountId(primaryAccount.getAccountId())
                    .accountNumber(primaryAccount.getAccountNumber())
                    .accountHolderName(primaryAccount.getAccountHolderName())
                    .accountType(primaryAccount.getAccountType())
                    .bankName(primaryAccount.getBankName())
                    .countryCode(primaryAccount.getCountryCode())
                    .riskRating(primaryAccount.getRiskRating())
                    .openedAt(primaryAccount.getOpenedAt())
                    .note("Customer details map to the primary originator Account entity per architectural model (SRS Out-of-Scope 5).")
                    .build();
        }

        // 4. Related Transactions
        List<CaseInvestigationResponse.RelatedTransactionDto> relatedTxns = new ArrayList<>();
        if (primaryBatchId != null) {
            List<FinancialTransaction> batchTxns = financialTransactionRepository.findByBatch_BatchId(primaryBatchId);
            for (FinancialTransaction ft : batchTxns) {
                if (!triggeringTxnIds.contains(ft.getTransactionId())) {
                    relatedTxns.add(CaseInvestigationResponse.RelatedTransactionDto.builder()
                            .transactionId(ft.getTransactionId())
                            .txnNo(ft.getTxnNo())
                            .amount(ft.getAmount())
                            .currency(ft.getCurrency())
                            .txnType(ft.getTxnType())
                            .direction(ft.getDirection())
                            .counterpartyName(ft.getCounterpartyName())
                            .counterpartyAccountNo(ft.getCounterpartyAccountNo())
                            .txnTimestamp(ft.getTxnTimestamp())
                            .batchId(primaryBatchId)
                            .batchCode(ft.getBatch() != null ? ft.getBatch().getBatchCode() : null)
                            .relationType("SAME_BATCH")
                            .build());
                }
            }
        }

        // 5. Customer Transaction History (paginated)
        org.springframework.data.domain.Page<CaseInvestigationResponse.TransactionHistoryDto> customerHistory = org.springframework.data.domain.Page.empty();
        List<FinancialTransaction> fullAccountTxns = new ArrayList<>();
        if (primaryAccount != null) {
            org.springframework.data.domain.Page<FinancialTransaction> historyPage = financialTransactionRepository.findByOriginatorAccount_AccountId(primaryAccount.getAccountId(), pageable);
            customerHistory = historyPage.map(ft -> CaseInvestigationResponse.TransactionHistoryDto.builder()
                    .transactionId(ft.getTransactionId())
                    .txnNo(ft.getTxnNo())
                    .amount(ft.getAmount())
                    .currency(ft.getCurrency())
                    .txnType(ft.getTxnType())
                    .direction(ft.getDirection())
                    .counterpartyName(ft.getCounterpartyName())
                    .counterpartyAccountNo(ft.getCounterpartyAccountNo())
                    .counterpartyBank(ft.getCounterpartyBank())
                    .counterpartyCountryCode(ft.getCounterpartyCountryCode())
                    .txnTimestamp(ft.getTxnTimestamp())
                    .countryCode(ft.getCountryCode())
                    .batchId(ft.getBatch() != null ? ft.getBatch().getBatchId() : null)
                    .build());

            fullAccountTxns = financialTransactionRepository.findByOriginatorAccount_AccountId(primaryAccount.getAccountId());
        }

        // 6. Counterparties
        List<CaseInvestigationResponse.CounterpartySummaryDto> counterparties = new ArrayList<>();
        if (!fullAccountTxns.isEmpty()) {
            Map<String, List<FinancialTransaction>> grouped = fullAccountTxns.stream()
                    .filter(ft -> ft.getCounterpartyName() != null || ft.getCounterpartyAccountNo() != null)
                    .collect(Collectors.groupingBy(ft ->
                            (ft.getCounterpartyName() != null ? ft.getCounterpartyName().toLowerCase().trim() : "") + "|" +
                            (ft.getCounterpartyAccountNo() != null ? ft.getCounterpartyAccountNo().trim() : "")));

            for (List<FinancialTransaction> txList : grouped.values()) {
                if (!txList.isEmpty()) {
                    FinancialTransaction sample = txList.get(0);
                    java.math.BigDecimal totalAmt = txList.stream()
                            .map(FinancialTransaction::getAmount)
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

                    counterparties.add(CaseInvestigationResponse.CounterpartySummaryDto.builder()
                            .counterpartyName(sample.getCounterpartyName())
                            .counterpartyAccountNo(sample.getCounterpartyAccountNo())
                            .counterpartyBank(sample.getCounterpartyBank())
                            .counterpartyCountryCode(sample.getCounterpartyCountryCode())
                            .transactionCount(txList.size())
                            .totalAmount(totalAmt)
                            .build());
                }
            }
        }

        // 7. Linked Accounts
        List<CaseInvestigationResponse.LinkedAccountDto> linkedAccounts = new ArrayList<>();
        if (primaryAccount != null && primaryAccount.getAccountHolderName() != null) {
            List<Account> linkedAccEntities = accountRepository.findByAccountHolderNameIgnoreCaseAndAccountIdNot(
                    primaryAccount.getAccountHolderName(), primaryAccount.getAccountId());

            linkedAccounts = linkedAccEntities.stream().map(acc -> CaseInvestigationResponse.LinkedAccountDto.builder()
                    .accountId(acc.getAccountId())
                    .accountNumber(acc.getAccountNumber())
                    .accountHolderName(acc.getAccountHolderName())
                    .accountType(acc.getAccountType())
                    .bankName(acc.getBankName())
                    .countryCode(acc.getCountryCode())
                    .riskRating(acc.getRiskRating())
                    .openedAt(acc.getOpenedAt())
                    .build()).collect(Collectors.toList());
        }

        // 8. Historical Alerts
        List<CaseInvestigationResponse.HistoricalAlertDto> historicalAlerts = new ArrayList<>();
        if (primaryAccount != null) {
            List<Alert> alertEntities = alertRepository.findByTransaction_OriginatorAccount_AccountId(primaryAccount.getAccountId());
            historicalAlerts = alertEntities.stream().map(alt -> {
                Rule r = alt.getRule();
                FinancialTransaction ft = alt.getTransaction();
                AmlCase c = alt.getAmlCase();
                return CaseInvestigationResponse.HistoricalAlertDto.builder()
                        .alertId(alt.getAlertId())
                        .alertCode(alt.getAlertCode())
                        .severity(alt.getSeverity())
                        .alertStatus(alt.getAlertStatus())
                        .ruleCode(r != null ? r.getRuleCode() : null)
                        .ruleName(r != null ? r.getRuleName() : null)
                        .transactionId(ft != null ? ft.getTransactionId() : null)
                        .transactionTxnNo(ft != null ? ft.getTxnNo() : null)
                        .txnTimestamp(ft != null ? ft.getTxnTimestamp() : null)
                        .caseId(c != null ? c.getCaseId() : null)
                        .caseCode(c != null ? c.getCaseCode() : null)
                        .createdAt(alt.getCreatedAt())
                        .build();
            }).collect(Collectors.toList());
        }

        // 9. Historical Cases
        List<CaseInvestigationResponse.HistoricalCaseDto> historicalCases = new ArrayList<>();
        if (primaryAccount != null) {
            List<AmlCase> caseEntities = amlCaseRepository.findHistoricalCasesByAccountId(primaryAccount.getAccountId(), caseId);
            historicalCases = caseEntities.stream().map(c -> {
                Users officer = c.getAssignedTo();
                return CaseInvestigationResponse.HistoricalCaseDto.builder()
                        .caseId(c.getCaseId())
                        .caseCode(c.getCaseCode())
                        .status(c.getStatus())
                        .assignedToId(officer != null ? officer.getUserId() : null)
                        .assignedToName(officer != null ? (officer.getFirstName() + " " + officer.getLastName()).trim() : null)
                        .assignedToEmail(officer != null ? officer.getEmail() : null)
                        .createdAt(c.getCreatedAt())
                        .closedAt(c.getClosedAt())
                        .build();
            }).collect(Collectors.toList());
        }

        return CaseInvestigationResponse.builder()
                .caseSummary(caseSummary)
                .triggeringTransactions(triggeringTxns)
                .customerAccountProfile(customerProfile)
                .relatedTransactions(relatedTxns)
                .customerTransactionHistory(customerHistory)
                .counterparties(counterparties)
                .linkedAccounts(linkedAccounts)
                .historicalAlerts(historicalAlerts)
                .historicalCases(historicalCases)
                .build();
    }

    @Override
    @Transactional
    public CaseResponse closeCaseNoAction(UUID caseId, com.tss.aml.dtos.tenant.CloseCaseNoActionRequest request, CustomUserDetails currentUser) {
        AmlCase amlCase = amlCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case with ID " + caseId + " not found"));

        if (amlCase.getAssignedTo() == null || !amlCase.getAssignedTo().getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Access denied: Case is not assigned to you.");
        }

        if (request == null || request.getRationale() == null || request.getRationale().trim().isEmpty()) {
            throw new IllegalArgumentException("Rationale is required for no-action closure");
        }

        if (amlCase.getStatus() == CaseStatus.CLOSED_NO_ACTION || amlCase.getStatus() == CaseStatus.CLOSED_SAR_FILED) {
            throw new IllegalStateException("Cannot close case with status: " + amlCase.getStatus() + ". Case is already closed.");
        }

        if (amlCase.getStatus() != CaseStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot close case with status: " + amlCase.getStatus() + ". Case must be IN_PROGRESS to close as no-action.");
        }

        if (!caseNoteRepository.existsByAmlCase_CaseId(caseId)) {
            throw new IllegalArgumentException("Case must contain at least one investigation note before closure.");
        }

        amlCase.setFalsePositiveRationale(request.getRationale().trim());
        amlCase.setStatus(CaseStatus.CLOSED_NO_ACTION);
        amlCase.setClosedAt(LocalDateTime.now());
        amlCaseRepository.save(amlCase);

        Users currentUserEntity = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in user not found: " + currentUser.getUserId()));

        AuditLog auditLog = AuditLog.builder()
                .actor(currentUserEntity)
                .action("CASE_CLOSED_NO_ACTION")
                .entityType("CASE")
                .entityId(amlCase.getCaseId().toString())
                .details("Compliance Officer " + currentUserEntity.getEmail() + " closed case " + amlCase.getCaseCode() + " as CLOSED_NO_ACTION. Rationale: " + amlCase.getFalsePositiveRationale())
                .build();
        auditLogRepository.save(auditLog);

        log.info("Compliance Officer '{}' closed Case '{}' as CLOSED_NO_ACTION", currentUserEntity.getEmail(), amlCase.getCaseCode());

        return mapToCaseResponse(amlCase);
    }

    @Override
    @Transactional(readOnly = true)
    public SarStrPreviewResponse getSarStrPreview(UUID caseId, CustomUserDetails currentUser) {
        AmlCase amlCase = amlCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case with ID " + caseId + " not found"));

        if (amlCase.getAssignedTo() == null || !amlCase.getAssignedTo().getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Access denied: Case is not assigned to you.");
        }

        if (amlCase.getStatus() != CaseStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot preview SAR/STR for case with status: " + amlCase.getStatus() + ". Case must be IN_PROGRESS.");
        }

        Account account = null;
        List<SarStrPreviewResponse.TransactionSummaryDto> txns = new ArrayList<>();
        List<SarStrPreviewResponse.AlertSummaryDto> alerts = new ArrayList<>();

        if (amlCase.getAlerts() != null) {
            for (Alert alert : amlCase.getAlerts()) {
                if (alert.getRule() != null) {
                    alerts.add(SarStrPreviewResponse.AlertSummaryDto.builder()
                            .alertId(alert.getAlertId())
                            .alertCode(alert.getAlertCode())
                            .severity(alert.getSeverity() != null ? alert.getSeverity().name() : null)
                            .ruleCode(alert.getRule().getRuleCode())
                            .ruleName(alert.getRule().getRuleName())
                            .build());
                }

                FinancialTransaction txn = alert.getTransaction();
                if (txn != null) {
                    if (account == null && txn.getOriginatorAccount() != null) {
                        account = txn.getOriginatorAccount();
                    }
                    txns.add(SarStrPreviewResponse.TransactionSummaryDto.builder()
                            .transactionId(txn.getTransactionId())
                            .txnNo(txn.getTxnNo())
                            .amount(txn.getAmount())
                            .currency(txn.getCurrency())
                            .txnType(txn.getTxnType() != null ? txn.getTxnType().name() : null)
                            .direction(txn.getDirection() != null ? txn.getDirection().name() : null)
                            .counterpartyName(txn.getCounterpartyName())
                            .counterpartyAccountNo(txn.getCounterpartyAccountNo())
                            .counterpartyBank(txn.getCounterpartyBank())
                            .txnTimestamp(txn.getTxnTimestamp())
                            .build());
                }
            }
        }

        List<String> reportTypes = List.of(SarStrType.SAR.name(), SarStrType.STR.name());
        List<String> typologies = List.of(
                FiuTypologyCategory.STRUCTURING.name(),
                FiuTypologyCategory.LAYERING.name(),
                FiuTypologyCategory.PEP_TRANSACTION.name(),
                FiuTypologyCategory.FRAUD_RELATED_ML.name(),
                FiuTypologyCategory.VELOCITY_CHECK.name(),
                FiuTypologyCategory.GEOGRAPHIC_RISK.name(),
                FiuTypologyCategory.RAPID_PASS_THROUGH.name(),
                FiuTypologyCategory.DORMANT_ACCOUNT.name(),
                FiuTypologyCategory.UTURN_TRANSACTION.name(),
                FiuTypologyCategory.CIRCULAR_LOOPING.name(),
                FiuTypologyCategory.OTHER_SUSPICIOUS_ACTIVITY.name()
        );

        return SarStrPreviewResponse.builder()
                .caseId(amlCase.getCaseId())
                .caseCode(amlCase.getCaseCode())
                .caseStatus(amlCase.getStatus())
                .assignedToEmail(currentUser.getUsername())
                .accountId(account != null ? account.getAccountId() : null)
                .accountNumber(account != null ? account.getAccountNumber() : null)
                .accountHolderName(account != null ? account.getAccountHolderName() : null)
                .accountType(account != null && account.getAccountType() != null ? account.getAccountType().name() : null)
                .bankName(account != null ? account.getBankName() : null)
                .countryCode(account != null ? account.getCountryCode() : null)
                .riskRating(account != null && account.getRiskRating() != null ? account.getRiskRating().name() : null)
                .transactions(txns)
                .triggeredAlerts(alerts)
                .supportedReportTypes(reportTypes)
                .supportedTypologyCategories(typologies)
                .build();
    }

    @Override
    @Transactional
    public SarStrResponse fileSarStr(UUID caseId, SarStrFilingRequest request, CustomUserDetails currentUser) {
        AmlCase amlCase = amlCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case with ID " + caseId + " not found"));

        if (amlCase.getAssignedTo() == null || !amlCase.getAssignedTo().getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Access denied: Case is not assigned to you.");
        }

        if (amlCase.getStatus() == CaseStatus.CLOSED_SAR_FILED || amlCase.getStatus() == CaseStatus.CLOSED_NO_ACTION) {
            throw new IllegalStateException("Cannot file SAR/STR for closed case with status: " + amlCase.getStatus());
        }

        if (amlCase.getStatus() != CaseStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot file SAR/STR for case with status: " + amlCase.getStatus() + ". Case must be IN_PROGRESS.");
        }

        if (!caseNoteRepository.existsByAmlCase_CaseId(caseId)) {
            throw new IllegalArgumentException("Case must contain at least one investigation note before SAR/STR filing.");
        }

        if (sarStrRepository.existsByAmlCase_CaseId(caseId)) {
            throw new IllegalStateException("SAR/STR report has already been filed for case " + amlCase.getCaseCode());
        }

        if (request == null || request.getReportType() == null) {
            throw new IllegalArgumentException("Report type (SAR/STR) is required");
        }

        if (request.getTypologyCategory() == null) {
            throw new IllegalArgumentException("Typology category is required");
        }

        if (request.getDescriptionOfActivity() == null || request.getDescriptionOfActivity().trim().isEmpty()) {
            throw new IllegalArgumentException("Description of activity is required");
        }

        if (request.getBasisForSuspicion() == null || request.getBasisForSuspicion().trim().isEmpty()) {
            throw new IllegalArgumentException("Basis for suspicion is required");
        }

        if (request.getSupportingEvidence() == null || request.getSupportingEvidence().trim().isEmpty()) {
            throw new IllegalArgumentException("Supporting evidence is required");
        }

        Users currentUserEntity = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in user not found: " + currentUser.getUserId()));

        String refNo = "SAR-2026-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String pdfRef = refNo + ".pdf";

        SarStr sarStr = SarStr.builder()
                .amlCase(amlCase)
                .reportType(request.getReportType())
                .typologyCategory(request.getTypologyCategory().name())
                .descriptionOfActivity(request.getDescriptionOfActivity().trim())
                .basisForSuspicion(request.getBasisForSuspicion().trim())
                .supportingEvidence(request.getSupportingEvidence().trim())
                .referenceNumber(refNo)
                .pdfReference(pdfRef)
                .filedBy(currentUserEntity)
                .submittedAt(LocalDateTime.now())
                .build();

        byte[] pdfBytes = SarStrPdfGenerator.generateSarStrPdf(sarStr);
        sarStr.setPdfContent(pdfBytes);

        SarStr savedSarStr = sarStrRepository.save(sarStr);

        amlCase.setStatus(CaseStatus.CLOSED_SAR_FILED);
        amlCase.setClosedAt(LocalDateTime.now());
        amlCaseRepository.save(amlCase);

        AuditLog auditLog = AuditLog.builder()
                .actor(currentUserEntity)
                .action("SAR_STR_FILED")
                .entityType("SarStr")
                .entityId(savedSarStr.getSarStrId().toString())
                .details("Compliance Officer " + currentUserEntity.getEmail() + " filed " + savedSarStr.getReportType() + " report " + refNo + " for case " + amlCase.getCaseCode() + ". Typology: " + savedSarStr.getTypologyCategory())
                .build();
        auditLogRepository.save(auditLog);

        log.info("Compliance Officer '{}' filed {} report '{}' for Case '{}'",
                currentUserEntity.getEmail(), savedSarStr.getReportType(), refNo, amlCase.getCaseCode());

        return mapToSarStrResponse(savedSarStr);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getSarStrPdf(UUID caseId, CustomUserDetails currentUser) {
        AmlCase amlCase = amlCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case with ID " + caseId + " not found"));

        if (amlCase.getAssignedTo() == null || !amlCase.getAssignedTo().getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Access denied: Case is not assigned to you.");
        }

        SarStr sarStr = sarStrRepository.findByAmlCase_CaseId(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("SAR/STR report not found for case ID " + caseId));

        if (sarStr.getPdfContent() == null) {
            throw new ResourceNotFoundException("PDF content not found for SAR/STR report " + sarStr.getReferenceNumber());
        }

        return sarStr.getPdfContent();
    }

    private SarStrResponse mapToSarStrResponse(SarStr sarStr) {
        Account account = null;
        if (sarStr.getAmlCase() != null && sarStr.getAmlCase().getAlerts() != null) {
            for (Alert alert : sarStr.getAmlCase().getAlerts()) {
                if (alert.getTransaction() != null && alert.getTransaction().getOriginatorAccount() != null) {
                    account = alert.getTransaction().getOriginatorAccount();
                    break;
                }
            }
        }

        return SarStrResponse.builder()
                .sarStrId(sarStr.getSarStrId())
                .caseId(sarStr.getAmlCase() != null ? sarStr.getAmlCase().getCaseId() : null)
                .caseCode(sarStr.getAmlCase() != null ? sarStr.getAmlCase().getCaseCode() : null)
                .reportType(sarStr.getReportType())
                .typologyCategory(sarStr.getTypologyCategory())
                .descriptionOfActivity(sarStr.getDescriptionOfActivity())
                .basisForSuspicion(sarStr.getBasisForSuspicion())
                .supportingEvidence(sarStr.getSupportingEvidence())
                .referenceNumber(sarStr.getReferenceNumber())
                .pdfReference(sarStr.getPdfReference())
                .filedById(sarStr.getFiledBy() != null ? sarStr.getFiledBy().getUserId() : null)
                .filedByName(sarStr.getFiledBy() != null ? sarStr.getFiledBy().getFirstName() + " " + sarStr.getFiledBy().getLastName() : null)
                .filedByEmail(sarStr.getFiledBy() != null ? sarStr.getFiledBy().getEmail() : null)
                .submittedAt(sarStr.getSubmittedAt())
                .accountNumber(account != null ? account.getAccountNumber() : null)
                .accountHolderName(account != null ? account.getAccountHolderName() : null)
                .build();
    }
}
