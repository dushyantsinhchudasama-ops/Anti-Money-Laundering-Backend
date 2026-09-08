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

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceOfficerServiceImpl implements ComplianceOfficerService {

    private final AmlCaseRepository amlCaseRepository;
    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

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
}
