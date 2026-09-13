package com.tss.aml.dtos.tenant;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.aml.enums.CaseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SarStrPreviewResponse {

    private UUID caseId;
    private String caseCode;
    private CaseStatus caseStatus;
    private String assignedToEmail;

    // Pre-populated Account / Customer Subject Information
    private UUID accountId;
    @JsonAlias({"primaryAccountNo"})
    private String accountNumber;
    @JsonAlias({"primaryAccountHolder"})
    private String accountHolderName;
    private String accountType;
    private String bankName;
    private String countryCode;
    private String riskRating;

    private String primaryAccountNo;
    private String primaryAccountHolder;
    private BigDecimal totalAlertAmount;
    private Integer alertCount;
    private String triggeringRulesSummary;
    private String suggestedNarrative;

    // Pre-populated Triggering & Related Transactions
    private List<TransactionSummaryDto> transactions;

    // Pre-populated Triggered Alerts & Rules
    private List<AlertSummaryDto> triggeredAlerts;

    // Standard Supported Options for CO Filing Form
    private List<String> supportedReportTypes;
    private List<String> supportedTypologyCategories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionSummaryDto {
        private UUID transactionId;
        private String txnNo;
        private BigDecimal amount;
        private String currency;
        private String txnType;
        private String direction;
        private String counterpartyName;
        private String counterpartyAccountNo;
        private String counterpartyBank;
        private LocalDateTime txnTimestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertSummaryDto {
        private UUID alertId;
        private String alertCode;
        private String severity;
        private String ruleCode;
        private String ruleName;
    }
}
