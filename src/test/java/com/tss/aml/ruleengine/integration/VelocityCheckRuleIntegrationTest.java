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
import com.tss.aml.ruleengine.evaluators.VelocityEvaluator;
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

class VelocityCheckRuleIntegrationTest {

    private RuleEngineService ruleEngineService;
    private FinancialTransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        RuleParameterParser parser = new RuleParameterParser(new ObjectMapper());
        transactionRepository = Mockito.mock(FinancialTransactionRepository.class);
        VelocityEvaluator velocityEvaluator = new VelocityEvaluator(parser, transactionRepository);
        AlertRepository alertRepository = Mockito.mock(AlertRepository.class);

        ruleEngineService = new RuleEngineService(
                List.of(velocityEvaluator),
                alertRepository
        );
        ruleEngineService.init();
    }

    @Test
    void test150VelocityCheckScenarios_withExactExpectedMatches() {
        Account highVelocityAccount = FinancialTransactionTestFactory.createAccount("ACC-VEL-SUITE", "High Velocity User", null, "US");
        Account normalVelocityAccount = FinancialTransactionTestFactory.createAccount("ACC-NORM-VEL", "Normal Velocity User", null, "US");

        Rule velocityRule = FinancialTransactionTestFactory.createRule(
                "R-VEL-5",
                "High Transaction Frequency Spike",
                RuleTypology.VELOCITY_CHECK,
                AlertSeverity.HIGH,
                "{\"windowDays\": 1, \"maxTransactionCount\": 5}"
        );

        List<FinancialTransaction> transactions = new ArrayList<>();
        Set<String> expectedAlertTxnNos = new HashSet<>();

        // Mocks:
        // highVelocityAccount count in window = 8 (> 5 limit) -> Positive
        // normalVelocityAccount count in window = 3 (<= 5 limit) -> Negative
        when(transactionRepository.countTransactionsInWindow(eq(highVelocityAccount), any(), any())).thenReturn(8);
        when(transactionRepository.countTransactionsInWindow(eq(normalVelocityAccount), any(), any())).thenReturn(3);

        // Generate 150 scenarios:
        // Scenarios 1..70: High Velocity Account (Positive -> 70 alerts)
        // Scenarios 71..150: Normal Velocity Account (Negative)
        for (int i = 1; i <= 150; i++) {
            String txnNo = "TXN-VEL-" + i;
            Account acc;

            if (i <= 70) {
                acc = highVelocityAccount;
                expectedAlertTxnNos.add(txnNo);
            } else {
                acc = normalVelocityAccount;
            }

            FinancialTransaction txn = FinancialTransactionTestFactory.createTransaction(
                    txnNo, acc, new BigDecimal("100.00"), "USD", null, null,
                    "COUNTER-VEL-" + i, "US",
                    FinancialTransactionTestFactory.BASE_TIME.plusMinutes(i), "US"
            );
            transactions.add(txn);
        }

        // Execute Rule Engine Flow
        List<Alert> actualAlerts = ruleEngineService.evaluateBatch(transactions, List.of(velocityRule));

        // Assert Alert Count matches exactly 70
        assertEquals(70, actualAlerts.size(), "Should produce exactly 70 alerts for high velocity transaction spikes");

        // Verify alert properties and exact matching transaction numbers
        Set<String> actualAlertTxnNos = new HashSet<>();
        for (Alert alert : actualAlerts) {
            assertEquals(AlertSeverity.HIGH, alert.getSeverity());
            assertEquals(velocityRule, alert.getRule());
            actualAlertTxnNos.add(alert.getTransaction().getTxnNo());
        }

        assertEquals(expectedAlertTxnNos, actualAlertTxnNos, "Generated alert transactions must match high velocity transactions");
    }
}
