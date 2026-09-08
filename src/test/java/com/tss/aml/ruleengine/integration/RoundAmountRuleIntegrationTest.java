package com.tss.aml.ruleengine.integration;

import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.tenant.Account;
import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.repositories.AlertRepository;
import com.tss.aml.ruleengine.RuleEngineService;
import com.tss.aml.ruleengine.evaluators.RoundAmountEvaluator;
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

class RoundAmountRuleIntegrationTest {

    private RuleEngineService ruleEngineService;

    @BeforeEach
    void setUp() {
        RuleParameterParser parser = new RuleParameterParser(new ObjectMapper());
        RoundAmountEvaluator roundAmountEvaluator = new RoundAmountEvaluator(parser);
        AlertRepository alertRepository = Mockito.mock(AlertRepository.class);

        ruleEngineService = new RuleEngineService(
                List.of(roundAmountEvaluator),
                alertRepository
        );
        ruleEngineService.init();
    }

    @Test
    void test150RoundAmountScenarios_withExactExpectedMatches() {
        Account account = FinancialTransactionTestFactory.createAccount("ACC-ROUND-150", "Round Test User", null, "US");
        Rule roundRule = FinancialTransactionTestFactory.createRule(
                "R-ROUND-1000",
                "Round Amount Flagging Modulo 1000",
                RuleTypology.ROUND_AMOUNT_FLAGGING,
                AlertSeverity.MEDIUM,
                "{\"moduloThreshold\": 1000.00}"
        );

        List<FinancialTransaction> transactions = new ArrayList<>();
        Set<String> expectedAlertTxnNos = new HashSet<>();

        // Generate 150 scenarios:
        // 75 Positive (exact multiples of 1000, e.g., 1000, 2000, 3000... 75000)
        // 75 Negative (non-round amounts, e.g., 1000.50, 1001.00, 999.99... decimal values)
        for (int i = 1; i <= 150; i++) {
            String txnNo = "TXN-ROUND-" + i;
            BigDecimal amount;
            boolean isPositiveMatch;

            if (i <= 75) {
                // Exact multiples of 1000 (Positive)
                amount = new BigDecimal(i * 1000);
                isPositiveMatch = true;
                expectedAlertTxnNos.add(txnNo);
            } else {
                // Non-multiples (Negative)
                amount = new BigDecimal((i * 1000) + 50.75);
                isPositiveMatch = false;
            }

            FinancialTransaction txn = FinancialTransactionTestFactory.createTransaction(
                    txnNo, account, amount, "USD", null, null, "COUNTER-01", "US",
                    FinancialTransactionTestFactory.BASE_TIME.plusMinutes(i), "US"
            );
            transactions.add(txn);
        }

        // Execute Rule Engine Flow
        List<Alert> actualAlerts = ruleEngineService.evaluateBatch(transactions, List.of(roundRule));

        // Assert Alert Count matches exactly 75
        assertEquals(75, actualAlerts.size(), "Should produce exactly 75 alerts for round amounts");

        // Verify alert properties and exact matching transaction numbers
        Set<String> actualAlertTxnNos = new HashSet<>();
        for (Alert alert : actualAlerts) {
            assertEquals(AlertSeverity.MEDIUM, alert.getSeverity());
            assertEquals(roundRule, alert.getRule());
            actualAlertTxnNos.add(alert.getTransaction().getTxnNo());
        }

        assertEquals(expectedAlertTxnNos, actualAlertTxnNos, "Generated alert transactions must exactly match expected round transactions");
    }
}
