package com.tss.aml.ruleengine.integration;

import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.tenant.Account;
import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.RiskRating;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.repositories.AlertRepository;
import com.tss.aml.ruleengine.RuleEngineService;
import com.tss.aml.ruleengine.evaluators.PepEvaluator;
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

class PepExposureRuleIntegrationTest {

    private RuleEngineService ruleEngineService;

    @BeforeEach
    void setUp() {
        RuleParameterParser parser = new RuleParameterParser(new ObjectMapper());
        PepEvaluator pepEvaluator = new PepEvaluator(parser);
        AlertRepository alertRepository = Mockito.mock(AlertRepository.class);

        ruleEngineService = new RuleEngineService(
                List.of(pepEvaluator),
                alertRepository
        );
        ruleEngineService.init();
    }

    @Test
    void test150PepExposureScenarios_withExactExpectedMatches() {
        Account pepHighAccount = FinancialTransactionTestFactory.createAccount("ACC-PEP-HIGH", "PEP Official", RiskRating.HIGH, "US");
        Account lowRiskAccount = FinancialTransactionTestFactory.createAccount("ACC-LOW-RISK", "Regular User", RiskRating.LOW, "US");
        Account medRiskAccount = FinancialTransactionTestFactory.createAccount("ACC-MED-RISK", "Medium User", RiskRating.MEDIUM, "US");

        Rule pepRule = FinancialTransactionTestFactory.createRule(
                "R-PEP-5000",
                "PEP High Value Screening",
                RuleTypology.PEP_EXPOSURE,
                AlertSeverity.HIGH,
                "{\"minAmountThreshold\": 5000.00}"
        );

        List<FinancialTransaction> transactions = new ArrayList<>();
        Set<String> expectedAlertTxnNos = new HashSet<>();

        // Generate 150 scenarios:
        // Scenarios 1..50: PEP High-Risk Account + Amount >= 5000 (Positive -> 50 alerts)
        // Scenarios 51..100: PEP High-Risk Account + Amount < 5000 (Negative)
        // Scenarios 101..125: Medium-Risk Account + Amount >= 5000 (Negative)
        // Scenarios 126..150: Low-Risk Account + Amount >= 5000 (Negative)
        for (int i = 1; i <= 150; i++) {
            String txnNo = "TXN-PEP-" + i;
            Account acc;
            BigDecimal amount;

            if (i <= 50) {
                acc = pepHighAccount;
                amount = new BigDecimal(5000 + (i * 100)); // >= 5000
                expectedAlertTxnNos.add(txnNo);
            } else if (i <= 100) {
                acc = pepHighAccount;
                amount = new BigDecimal(4999 - (i % 1000)); // < 5000
            } else if (i <= 125) {
                acc = medRiskAccount;
                amount = new BigDecimal(10000);
            } else {
                acc = lowRiskAccount;
                amount = new BigDecimal(20000);
            }

            FinancialTransaction txn = FinancialTransactionTestFactory.createTransaction(
                    txnNo, acc, amount, "USD", null, null,
                    "COUNTER-PEP-" + i, "US",
                    FinancialTransactionTestFactory.BASE_TIME.plusMinutes(i), "US"
            );
            transactions.add(txn);
        }

        // Execute Rule Engine Flow
        List<Alert> actualAlerts = ruleEngineService.evaluateBatch(transactions, List.of(pepRule));

        // Assert Alert Count matches exactly 50
        assertEquals(50, actualAlerts.size(), "Should produce exactly 50 alerts for PEP high-value transactions");

        // Verify alert properties and exact matching transaction numbers
        Set<String> actualAlertTxnNos = new HashSet<>();
        for (Alert alert : actualAlerts) {
            assertEquals(AlertSeverity.HIGH, alert.getSeverity());
            assertEquals(pepRule, alert.getRule());
            actualAlertTxnNos.add(alert.getTransaction().getTxnNo());
        }

        assertEquals(expectedAlertTxnNos, actualAlertTxnNos, "Generated alert transactions must match high-risk PEP transactions");
    }
}
