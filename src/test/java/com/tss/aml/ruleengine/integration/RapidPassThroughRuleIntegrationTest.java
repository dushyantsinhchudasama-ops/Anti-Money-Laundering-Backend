package com.tss.aml.ruleengine.integration;

import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.tenant.Account;
import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.enums.TransactionDirection;
import com.tss.aml.repositories.AlertRepository;
import com.tss.aml.repositories.FinancialTransactionRepository;
import com.tss.aml.ruleengine.RuleEngineService;
import com.tss.aml.ruleengine.evaluators.RapidPassThroughEvaluator;
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

class RapidPassThroughRuleIntegrationTest {

    private RuleEngineService ruleEngineService;
    private FinancialTransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        RuleParameterParser parser = new RuleParameterParser(new ObjectMapper());
        transactionRepository = Mockito.mock(FinancialTransactionRepository.class);
        RapidPassThroughEvaluator rapidPassThroughEvaluator = new RapidPassThroughEvaluator(parser, transactionRepository);
        AlertRepository alertRepository = Mockito.mock(AlertRepository.class);

        ruleEngineService = new RuleEngineService(
                List.of(rapidPassThroughEvaluator),
                alertRepository
        );
        ruleEngineService.init();
    }

    @Test
    void test150RapidPassThroughScenarios_withExactExpectedMatches() {
        Account funnelAccount = FinancialTransactionTestFactory.createAccount("ACC-FUNNEL-SUITE", "Funnel Account", null, "US");
        Account retainAccount = FinancialTransactionTestFactory.createAccount("ACC-RETAIN-SUITE", "Retaining Account", null, "US");

        Rule passThroughRule = FinancialTransactionTestFactory.createRule(
                "R-PASSTHRU-01",
                "Rapid Pass-Through Funneling",
                RuleTypology.RAPID_PASS_THROUGH,
                AlertSeverity.HIGH,
                "{\"windowHours\": 24, \"minAmountThreshold\": 10000.00, \"passThroughRatio\": 0.80}"
        );

        List<FinancialTransaction> transactions = new ArrayList<>();
        Set<String> expectedAlertTxnNos = new HashSet<>();

        // Mocks:
        // funnelAccount: incoming = 100,000, outgoing = 90,000 (ratio 0.90 >= 0.80) -> Positive
        // retainAccount: incoming = 100,000, outgoing = 30,000 (ratio 0.30 < 0.80) -> Negative
        when(transactionRepository.sumAmountByDirectionInWindow(eq(funnelAccount), eq(TransactionDirection.IN), any(), any()))
                .thenReturn(new BigDecimal("100000.00"));
        when(transactionRepository.sumAmountByDirectionInWindow(eq(funnelAccount), eq(TransactionDirection.OUT), any(), any()))
                .thenReturn(new BigDecimal("90000.00"));

        when(transactionRepository.sumAmountByDirectionInWindow(eq(retainAccount), eq(TransactionDirection.IN), any(), any()))
                .thenReturn(new BigDecimal("100000.00"));
        when(transactionRepository.sumAmountByDirectionInWindow(eq(retainAccount), eq(TransactionDirection.OUT), any(), any()))
                .thenReturn(new BigDecimal("30000.00"));

        // Generate 150 scenarios:
        // Scenarios 1..80: Funnel Account (Positive -> 80 alerts)
        // Scenarios 81..150: Retain Account (Negative)
        for (int i = 1; i <= 150; i++) {
            String txnNo = "TXN-PASSTHRU-" + i;
            Account acc;

            if (i <= 80) {
                acc = funnelAccount;
                expectedAlertTxnNos.add(txnNo);
            } else {
                acc = retainAccount;
            }

            FinancialTransaction txn = FinancialTransactionTestFactory.createTransaction(
                    txnNo, acc, new BigDecimal("15000.00"), "USD", null, TransactionDirection.OUT,
                    "COUNTER-FUNNEL-" + i, "US",
                    FinancialTransactionTestFactory.BASE_TIME.plusMinutes(i), "US"
            );
            transactions.add(txn);
        }

        // Execute Rule Engine Flow
        List<Alert> actualAlerts = ruleEngineService.evaluateBatch(transactions, List.of(passThroughRule));

        // Assert Alert Count matches exactly 80
        assertEquals(80, actualAlerts.size(), "Should produce exactly 80 alerts for rapid pass-through funneling");

        // Verify alert properties and exact matching transaction numbers
        Set<String> actualAlertTxnNos = new HashSet<>();
        for (Alert alert : actualAlerts) {
            assertEquals(AlertSeverity.HIGH, alert.getSeverity());
            assertEquals(passThroughRule, alert.getRule());
            actualAlertTxnNos.add(alert.getTransaction().getTxnNo());
        }

        assertEquals(expectedAlertTxnNos, actualAlertTxnNos, "Generated alert transactions must match rapid pass-through transactions");
    }
}
