package com.tss.aml.dtos.tenant;

import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.AlertStatus;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.enums.TransactionDirection;
import com.tss.aml.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertDetailResponse {
    private UUID alertId;
    private String alertCode;
    private AlertSeverity severity;
    private AlertStatus alertStatus;
    private LocalDateTime createdAt;

    // Transaction Details
    private UUID transactionId;
    private String transactionTxnNo;
    private BigDecimal amount;
    private String currency;
    private TransactionType transactionType;
    private TransactionDirection direction;
    private String originatorAccountNumber;
    private String counterpartyName;
    private String counterpartyAccountNo;
    private String counterpartyBank;
    private String counterpartyCountryCode;
    private LocalDateTime transactionTimestamp;

    // Triggered Rule Details
    private UUID ruleId;
    private String ruleCode;
    private String ruleName;
    private String ruleDescription;
    private RuleTypology typology;
    private Object ruleParameters;


    // Case Reference
    private UUID caseId;
    private String caseCode;
}
