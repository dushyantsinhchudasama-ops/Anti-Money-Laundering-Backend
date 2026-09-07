package com.tss.aml.ruleengine.integration;

import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.tenant.Account;
import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.repositories.AlertRepository;
import com.tss.aml.ruleengine.RuleEngineService;
import com.tss.aml.ruleengine.evaluators.GeographicRiskRuleEvaluator;
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

class GeographicRiskRuleIntegrationTest {

    private RuleEngineService ruleEngineService;

    @BeforeEach
    void setUp() {
        RuleParameterParser parser = new RuleParameterParser(new ObjectMapper());
        GeographicRiskRuleEvaluator geoEvaluator = new GeographicRiskRuleEvaluator(parser);
        AlertRepository alertRepository = Mockito.mock(AlertRepository.class);

        ruleEngineService = new RuleEngineService(
                List.of(geoEvaluator),
                alertRepository
        );
        ruleEngineService.init();
    }

    @Test
    void test150GeographicRiskScenarios_withExactExpectedMatches() {
        Account account = FinancialTransactionTestFactory.createAccount("ACC-GEO-150", "Geo Test User", null, "US");
        Rule geoRule = FinancialTransactionTestFactory.createRule(
                "R-GEO-HIGH-RISK",
                "FATF High Risk Countries",
                RuleTypology.GEOGRAPHIC_RISK,
                AlertSeverity.HIGH,
                "{\"highRiskCountries\": [\"IR\", \"KP\", \"MM\", \"SY\"]}"
        );

        List<FinancialTransaction> transactions = new ArrayList<>();
        Set<String> expectedAlertTxnNos = new HashSet<>();

        List<String> highRiskList = List.of("IR", "KP", "MM", "SY");
        List<String> safeList = List.of("US", "CA", "GB", "DE", "FR", "JP", "AU");

        // Generate 150 scenarios:
        // Scenarios 1..50: Originator country high-risk (Positive)
        // Scenarios 51..100: Counterparty country high-risk (Positive)
        // Scenarios 101..150: Neither high-risk (Negative)
        for (int i = 1; i <= 150; i++) {
            String txnNo = "TXN-GEO-" + i;
            String originatorCountry;
            String counterpartyCountry;

            if (i <= 50) {
                originatorCountry = highRiskList.get((i - 1) % highRiskList.size());
                counterpartyCountry = safeList.get((i - 1) % safeList.size());
                expectedAlertTxnNos.add(txnNo);
            } else if (i <= 100) {
                originatorCountry = safeList.get((i - 51) % safeList.size());
                counterpartyCountry = highRiskList.get((i - 51) % highRiskList.size());
                expectedAlertTxnNos.add(txnNo);
            } else {
                originatorCountry = safeList.get((i - 101) % safeList.size());
                counterpartyCountry = safeList.get((i - 101) % safeList.size());
            }

            FinancialTransaction txn = FinancialTransactionTestFactory.createTransaction(
                    txnNo, account, new BigDecimal("500.00"), "USD", null, null,
                    "COUNTER-GEO-" + i, counterpartyCountry,
                    FinancialTransactionTestFactory.BASE_TIME.plusMinutes(i), originatorCountry
            );
            transactions.add(txn);
        }

        // Execute Rule Engine Flow
        List<Alert> actualAlerts = ruleEngineService.evaluateBatch(transactions, List.of(geoRule));

        // Assert Alert Count matches exactly 100
        assertEquals(100, actualAlerts.size(), "Should produce exactly 100 alerts for high-risk geographic transactions");

        // Verify alert properties and exact matching transaction numbers
        Set<String> actualAlertTxnNos = new HashSet<>();
        for (Alert alert : actualAlerts) {
            assertEquals(AlertSeverity.HIGH, alert.getSeverity());
            assertEquals(geoRule, alert.getRule());
            actualAlertTxnNos.add(alert.getTransaction().getTxnNo());
        }

        assertEquals(expectedAlertTxnNos, actualAlertTxnNos, "Generated alert transactions must match high-risk country transactions");
    }
}
