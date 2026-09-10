package com.tss.aml.dtos.tenant;

import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.AlertStatus;
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
public class AlertResponse {
    private UUID alertId;
    private String alertCode;
    private UUID transactionId;
    private String transactionTxnNo;
    private BigDecimal amount;
    private String currency;
    private UUID ruleId;
    private String ruleCode;
    private String ruleName;
    private AlertSeverity severity;
    private AlertStatus alertStatus;
    private UUID caseId;
    private String caseCode;
    private LocalDateTime createdAt;
}
