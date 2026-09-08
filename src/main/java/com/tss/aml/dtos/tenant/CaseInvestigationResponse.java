package com.tss.aml.dtos.tenant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.aml.enums.AccountType;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.AlertStatus;
import com.tss.aml.enums.CaseStatus;
import com.tss.aml.enums.RiskRating;
import com.tss.aml.enums.TransactionDirection;
import com.tss.aml.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CaseInvestigationResponse {

    private CaseSummaryDto caseSummary;
    private List<TriggeringTransactionDto> triggeringTransactions;
    private CustomerAccountProfileDto customerAccountProfile;
    private List<RelatedTransactionDto> relatedTransactions;
    private Page<TransactionHistoryDto> customerTransactionHistory;
    private List<CounterpartySummaryDto> counterparties;
    private List<LinkedAccountDto> linkedAccounts;
    private List<HistoricalAlertDto> historicalAlerts;
    private List<HistoricalCaseDto> historicalCases;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaseSummaryDto {
        private UUID caseId;
        private String caseCode;
        private CaseStatus status;
        private UUID assignedToId;
        private String assignedToName;
        private String assignedToEmail;
        private UUID createdById;
        private String createdByName;
        private String falsePositiveRationale;
        private LocalDateTime createdAt;
        private LocalDateTime closedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TriggeringTransactionDto {
        private UUID transactionId;
        private String txnNo;
        private BigDecimal amount;
        private String currency;
        private TransactionType txnType;
        private TransactionDirection direction;
        private String counterpartyName;
        private String counterpartyAccountNo;
        private String counterpartyBank;
        private String counterpartyCountryCode;
        private LocalDateTime txnTimestamp;
        private String countryCode;
        private UUID batchId;
        private String batchCode;
        private String alertCode;
        private AlertSeverity alertSeverity;
        private String ruleCode;
        private String ruleName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerAccountProfileDto {
        private UUID accountId;
        private String accountNumber;
        private String accountHolderName;
        private AccountType accountType;
        private String bankName;
        private String countryCode;
        private RiskRating riskRating;
        private LocalDateTime openedAt;
        private String note; // Documents architecture decision (Account maps customer profile)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedTransactionDto {
        private UUID transactionId;
        private String txnNo;
        private BigDecimal amount;
        private String currency;
        private TransactionType txnType;
        private TransactionDirection direction;
        private String counterpartyName;
        private String counterpartyAccountNo;
        private LocalDateTime txnTimestamp;
        private UUID batchId;
        private String batchCode;
        private String relationType; // e.g. "SAME_BATCH", "SAME_ACCOUNT"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionHistoryDto {
        private UUID transactionId;
        private String txnNo;
        private BigDecimal amount;
        private String currency;
        private TransactionType txnType;
        private TransactionDirection direction;
        private String counterpartyName;
        private String counterpartyAccountNo;
        private String counterpartyBank;
        private String counterpartyCountryCode;
        private LocalDateTime txnTimestamp;
        private String countryCode;
        private UUID batchId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CounterpartySummaryDto {
        private String counterpartyName;
        private String counterpartyAccountNo;
        private String counterpartyBank;
        private String counterpartyCountryCode;
        private long transactionCount;
        private BigDecimal totalAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkedAccountDto {
        private UUID accountId;
        private String accountNumber;
        private String accountHolderName;
        private AccountType accountType;
        private String bankName;
        private String countryCode;
        private RiskRating riskRating;
        private LocalDateTime openedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalAlertDto {
        private UUID alertId;
        private String alertCode;
        private AlertSeverity severity;
        private AlertStatus alertStatus;
        private String ruleCode;
        private String ruleName;
        private UUID transactionId;
        private String transactionTxnNo;
        private LocalDateTime txnTimestamp;
        private UUID caseId;
        private String caseCode;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalCaseDto {
        private UUID caseId;
        private String caseCode;
        private CaseStatus status;
        private UUID assignedToId;
        private String assignedToName;
        private String assignedToEmail;
        private LocalDateTime createdAt;
        private LocalDateTime closedAt;
    }
}
