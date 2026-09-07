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
import com.tss.aml.ruleengine.evaluators.DormantAccountEvaluator;
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

class DormantAccountReactivationRuleIntegrationTest {

    private RuleEngineService ruleEngineService;
    private FinancialTransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        RuleParameterParser parser = new RuleParameterParser(new ObjectMapper());
        transactionRepository = Mockito.mock(FinancialTransactionRepository.class);
        DormantAccountEvaluator dormantEvaluator = new DormantAccountEvaluator(parser, transactionRepository);
        AlertRepository alertRepository = Mockito.mock(AlertRepository.class);

        ruleEngineService = new RuleEngineService(
                List.of(dormantEvaluator),
                alertRepository
        );
        ruleEngineService.init();
    }

    @Test
    void test150DormantAccountScenarios_withExactExpectedMatches() {
        Account dormantAccount = FinancialTransactionTestFactory.createAccount("ACC-DORMANT-SUITE", "Dormant User", null, "US");
        Account activeAccount = FinancialTransactionTestFactory.createAccount("ACC-ACTIVE-SUITE", "Active User", null, "US");

        Rule dormantRule = FinancialTransactionTestFactory.createRule(
                "R-DORMANT-10000",
                "Dormant Account Reactivation Flag",
                RuleTypology.DORMANT_ACCOUNT,
                AlertSeverity.HIGH,
                "{\"dormantDays\": 90, \"minAmountThreshold\": 10000.00}"
        );

        List<FinancialTransaction> transactions = new ArrayList<>();
        Set<String> expectedAlertTxnNos = new HashSet<>();

        // Configure Mocks for dormant vs active accounts:
        // dormantAccount has 0 prior transactions
        // activeAccount has 5 prior transactions
        when(transactionRepository.countPriorTransactions(eq(dormantAccount), any(), any())).thenReturn(0);
        when(transactionRepository.countPriorTransactions(eq(activeAccount), any(), any())).thenReturn(5);

        // Generate 150 scenarios:
        // Scenarios 1..60: Dormant Account + High Amount >= 10000 (Positive -> 60 alerts)
        // Scenarios 61..100: Dormant Account + Low Amount < 10000 (Negative)
        // Scenarios 101..150: Active Account + High Amount >= 10000 (Negative)
        for (int i = 1; i <= 150; i++) {
            String txnNo = "TXN-DORMANT-" + i;
            Account acc;
            BigDecimal amount;

            if (i <= 60) {
                acc = dormantAccount;
                amount = new BigDecimal(10000 + (i * 500));
                expectedAlertTxnNos.add(txnNo);
            } else if (i <= 100) {
                acc = dormantAccount;
                amount = new BigDecimal(9999 - (i % 1000));
            } else {
                acc = activeAccount;
                amount = new BigDecimal(50000);
            }

            FinancialTransaction txn = FinancialTransactionTestFactory.createTransaction(
                    txnNo, acc, amount, "USD", null, null,
                    "COUNTER-DORMANT-" + i, "US",
                    FinancialTransactionTestFactory.BASE_TIME.plusMinutes(i), "US"
            );
            transactions.add(txn);
        }

        // Execute Rule Engine Flow
        List<Alert> actualAlerts = ruleEngineService.evaluateBatch(transactions, List.of(dormantRule));

        // Assert Alert Count matches exactly 60
        assertEquals(60, actualAlerts.size(), "Should produce exactly 60 alerts for dormant account reactivation");

        // Verify alert properties and exact matching transaction numbers
        Set<String> actualAlertTxnNos = new HashSet<>();
        for (Alert alert : actualAlerts) {
            assertEquals(AlertSeverity.HIGH, alert.getSeverity());
            assertEquals(dormantRule, alert.getRule());
            actualAlertTxnNos.add(alert.getTransaction().getTxnNo());
        }

        assertEquals(expectedAlertTxnNos, actualAlertTxnNos, "Generated alert transactions must match dormant account reactivation transactions");
    }
}
