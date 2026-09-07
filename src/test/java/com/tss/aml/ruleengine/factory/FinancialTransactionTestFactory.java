package com.tss.aml.ruleengine.factory;

import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.tenant.Account;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reusable test factory for creating deterministic test entities and scenario objects.
 */
public class FinancialTransactionTestFactory {

    public static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 1, 1, 12, 0, 0);

    public static Account createAccount(String accountNumber, String name, RiskRating riskRating, String countryCode) {
        return Account.builder()
                .accountId(UUID.nameUUIDFromBytes(accountNumber.getBytes()))
                .accountNumber(accountNumber)
                .accountHolderName(name)
                .accountType(AccountType.SAVINGS)
                .bankName("Test Bank")
                .countryCode(countryCode != null ? countryCode : "US")
                .riskRating(riskRating != null ? riskRating : RiskRating.LOW)
                .openedAt(BASE_TIME.minusYears(2))
                .build();
    }

    public static FinancialTransaction createTransaction(
            String txnNo,
            Account originatorAccount,
            BigDecimal amount,
            String currency,
            TransactionType type,
            TransactionDirection direction,
            String counterpartyAccountNo,
            String counterpartyCountryCode,
            LocalDateTime timestamp,
            String countryCode
    ) {
        return FinancialTransaction.builder()
                .transactionId(UUID.nameUUIDFromBytes((txnNo + "_" + timestamp).getBytes()))
                .txnNo(txnNo)
                .originatorAccount(originatorAccount)
                .amount(amount)
                .currency(currency != null ? currency : "USD")
                .txnType(type != null ? type : TransactionType.NEFT)
                .direction(direction != null ? direction : TransactionDirection.OUT)
                .counterpartyAccountNo(counterpartyAccountNo)
                .counterpartyCountryCode(counterpartyCountryCode)
                .txnTimestamp(timestamp != null ? timestamp : BASE_TIME)
                .countryCode(countryCode != null ? countryCode : "US")
                .build();
    }

    private static final tools.jackson.databind.ObjectMapper MAPPER = new tools.jackson.databind.ObjectMapper();

    public static Rule createRule(String code, String name, RuleTypology typology, AlertSeverity severity, String parametersJson) {
        return createRule(code, name, typology, severity != null ? RuleSeverity.valueOf(severity.name()) : RuleSeverity.HIGH, parametersJson);
    }

    public static Rule createRule(String code, String name, RuleTypology typology, RuleSeverity severity, String parametersJson) {
        java.util.Map<String, Object> params = java.util.Collections.emptyMap();
        if (parametersJson != null) {
            try {
                params = MAPPER.readValue(parametersJson, java.util.Map.class);
            } catch (Exception e) {
                params = java.util.Map.of("raw", parametersJson);
            }
        }
        return createRule(code, name, typology, severity, params);
    }

    public static Rule createRule(String code, String name, RuleTypology typology, RuleSeverity severity, java.util.Map<String, Object> parameters) {
        return Rule.builder()
                .ruleId(UUID.nameUUIDFromBytes(code.getBytes()))
                .ruleCode(code)
                .ruleName(name)
                .typology(typology)
                .defaultSeverity(severity != null ? severity : RuleSeverity.HIGH)
                .status(RuleStatus.ACTIVE)
                .parameters(parameters)
                .build();
    }
}
