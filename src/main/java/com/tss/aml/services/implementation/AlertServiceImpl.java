package com.tss.aml.services.implementation;

import com.tss.aml.dtos.tenant.AlertDetailResponse;
import com.tss.aml.dtos.tenant.AlertResponse;
import com.tss.aml.dtos.tenant.AlertStatsResponse;
import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.entities.system.Rule;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.AlertStatus;
import com.tss.aml.exceptions.base.ResourceNotFoundException;
import com.tss.aml.repositories.AlertRepository;
import com.tss.aml.security.CustomUserDetails;
import com.tss.aml.services.interfaces.AlertService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Slf4j
public class AlertServiceImpl implements AlertService {

    private final AlertRepository alertRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<AlertResponse> getAlerts(AlertSeverity severity, UUID ruleId, AlertStatus status, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable, CustomUserDetails currentUser
    ) {
        Specification<Alert> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
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

            CriteriaBuilder.SimpleCase<Object, Integer> selectCase = cb.selectCase(root.get("severity"));
            selectCase.when(AlertSeverity.HIGH, 1);
            selectCase.when(AlertSeverity.MEDIUM, 2);
            Expression<Integer> severityOrder = selectCase.otherwise(3);

            if (query != null) {
                query.orderBy(cb.asc(severityOrder), cb.desc(root.get("createdAt")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Alert> alertPage = alertRepository.findAll(spec, pageable);
        return alertPage.map(this::mapToAlertResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AlertStatsResponse getAlertStats(CustomUserDetails currentUser) {
        long high = alertRepository.countBySeverity(AlertSeverity.HIGH);
        long medium = alertRepository.countBySeverity(AlertSeverity.MEDIUM);
        long low = alertRepository.countBySeverity(AlertSeverity.LOW);
        long open = alertRepository.countByAlertStatus(AlertStatus.OPEN);
        long total = alertRepository.count();

        return AlertStatsResponse.builder()
                .highSeverityCount(high)
                .mediumSeverityCount(medium)
                .lowSeverityCount(low)
                .openAlertsCount(open)
                .totalAlertsCount(total)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AlertDetailResponse getAlertDetail(UUID alertId, CustomUserDetails currentUser) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert with ID " + alertId + " not found"));

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
}
