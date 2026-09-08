package com.tss.aml.ruleengine.integration;

import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.tenant.Account;
import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.repositories.AlertRepository;
import com.tss.aml.repositories.FinancialTransactionRepository;
import com.tss.aml.ruleengine.RuleEngineService;
import com.tss.aml.ruleengine.evaluators.StructuringEvaluator;
import com.tss.aml.ruleengine.factory.FinancialTransactionTestFactory;
import com.tss.aml.ruleengine.util.RuleParameterParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class StructuringSmurfingRuleIntegrationTest {

    private RuleEngineService ruleEngineService;
    private FinancialTransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        RuleParameterParser parser = new RuleParameterParser(new ObjectMapper());
        transactionRepository = Mockito.mock(FinancialTransactionRepository.class);
        StructuringEvaluator structuringEvaluator = new StructuringEvaluator(parser, transactionRepository);
        AlertRepository alertRepository = Mockito.mock(AlertRepository.class);

        ruleEngineService = new RuleEngineService(
                List.of(structuringEvaluator),
                alertRepository
        );
        ruleEngineService.init();
    }

    @Test
    void test150StructuringScenarios_withExactExpectedMatches() {
        Account smurfingAccount = FinancialTransactionTestFactory.createAccount("ACC-SMURF-SUITE", "Smurf User", null, "US");
        Account normalAccount = FinancialTransactionTestFactory.createAccount("ACC-NORMAL-SUITE", "Normal User", null, "US");

        Rule structuringRule = FinancialTransactionTestFactory.createRule(
                "R-STRUCT-10000",
                "Structuring Smurfing Detection",
                RuleTypology.STRUCTURING_SMURFING,
                AlertSeverity.HIGH,
                "{\"windowDays\": 1, \"reportingThreshold\": 10000.00}"
        );

        List<FinancialTransaction> transactions = new ArrayList<>();
        Set<String> expectedAlertTxnNos = new HashSet<>();

        // Mocks:
        // smurfingAccount cumulative sum in window = 15000 (>= 10000 threshold)
        // normalAccount cumulative sum in window = 4000 (< 10000 threshold)
        when(transactionRepository.sumTransactionAmountsInWindow(eq(smurfingAccount), any(), any()))
                .thenReturn(new BigDecimal("15000.00"));
        when(transactionRepository.sumTransactionAmountsInWindow(eq(normalAccount), any(), any()))
                .thenReturn(new BigDecimal("4000.00"));

        // Generate 150 scenarios:
        // Scenarios 1..50: Smurfing Account + Individual Txn < 10000 (e.g. 9500) + Sum >= 10000 (Positive -> 50 alerts)
        // Scenarios 51..100: Smurfing Account + Individual Txn >= 10000 (e.g. 12000) (Negative -> Direct threshold transaction is reported directly, not smurfing)
        // Scenarios 101..150: Normal Account + Individual Txn < 10000 + Sum < 10000 (Negative)
        for (int i = 1; i <= 150; i++) {
            String txnNo = "TXN-STRUCT-" + i;
            Account acc;
            BigDecimal amount;

            if (i <= 50) {
                acc = smurfingAccount;
                amount = new BigDecimal("9500.00");
                expectedAlertTxnNos.add(txnNo);
            } else if (i <= 100) {
                acc = smurfingAccount;
                amount = new BigDecimal("12000.00"); // >= threshold
            } else {
                acc = normalAccount;
                amount = new BigDecimal("2000.00");
            }

            FinancialTransaction txn = FinancialTransactionTestFactory.createTransaction(
                    txnNo, acc, amount, "USD", null, null,
                    "COUNTER-STRUCT-" + i, "US",
                    FinancialTransactionTestFactory.BASE_TIME.plusMinutes(i), "US"
            );
            transactions.add(txn);
        }

        // Execute Rule Engine Flow
        List<Alert> actualAlerts = ruleEngineService.evaluateBatch(transactions, List.of(structuringRule));

        // Assert Alert Count matches exactly 50
        assertEquals(50, actualAlerts.size(), "Should produce exactly 50 alerts for structuring/smurfing transactions");

        // Verify alert properties and exact matching transaction numbers
        Set<String> actualAlertTxnNos = new HashSet<>();
        for (Alert alert : actualAlerts) {
            assertEquals(AlertSeverity.HIGH, alert.getSeverity());
            assertEquals(structuringRule, alert.getRule());
            actualAlertTxnNos.add(alert.getTransaction().getTxnNo());
        }

        assertEquals(expectedAlertTxnNos, actualAlertTxnNos, "Generated alert transactions must match structuring transactions");
    }
}
