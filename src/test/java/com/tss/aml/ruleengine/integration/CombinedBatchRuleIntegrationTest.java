package com.tss.aml.ruleengine.integration;

import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.tenant.Account;
import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.RiskRating;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.repositories.AlertRepository;
import com.tss.aml.repositories.FinancialTransactionRepository;
import com.tss.aml.ruleengine.RuleEngineService;
import com.tss.aml.ruleengine.evaluators.*;
import com.tss.aml.ruleengine.factory.FinancialTransactionTestFactory;
import com.tss.aml.ruleengine.util.RuleParameterParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombinedBatchRuleIntegrationTest {

    private RuleEngineService ruleEngineService;
    private FinancialTransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        RuleParameterParser parser = new RuleParameterParser(new ObjectMapper());
        transactionRepository = Mockito.mock(FinancialTransactionRepository.class);

        RoundAmountEvaluator roundAmountEvaluator = new RoundAmountEvaluator(parser);
        GeographicRiskRuleEvaluator geoEvaluator = new GeographicRiskRuleEvaluator(parser);
        PepEvaluator pepEvaluator = new PepEvaluator(parser);
        StructuringEvaluator structuringEvaluator = new StructuringEvaluator(parser, transactionRepository);
        VelocityEvaluator velocityEvaluator = new VelocityEvaluator(parser, transactionRepository);
        RapidPassThroughEvaluator rapidPassThroughEvaluator = new RapidPassThroughEvaluator(parser, transactionRepository);
        DormantAccountEvaluator dormantAccountEvaluator = new DormantAccountEvaluator(parser, transactionRepository);

        AlertRepository alertRepository = Mockito.mock(AlertRepository.class);

        ruleEngineService = new RuleEngineService(
                List.of(
                        roundAmountEvaluator,
                        geoEvaluator,
                        pepEvaluator,
                        structuringEvaluator,
                        velocityEvaluator,
                        rapidPassThroughEvaluator,
                        dormantAccountEvaluator
                ),
                alertRepository
        );
        ruleEngineService.init();
    }

    @Test
    void testCombined1050BatchScenarios_allImplementedRules() {
        Account regularAcc = FinancialTransactionTestFactory.createAccount("ACC-ALL-01", "Regular User", RiskRating.LOW, "US");
        Account pepAcc = FinancialTransactionTestFactory.createAccount("ACC-PEP-01", "PEP User", RiskRating.HIGH, "US");

        List<Rule> activeRules = List.of(
                FinancialTransactionTestFactory.createRule("R-ROUND", "Round", RuleTypology.ROUND_AMOUNT_FLAGGING, AlertSeverity.MEDIUM, "{\"moduloThreshold\": 1000.00}"),
                FinancialTransactionTestFactory.createRule("R-GEO", "Geo", RuleTypology.GEOGRAPHIC_RISK, AlertSeverity.HIGH, "{\"highRiskCountries\": [\"IR\", \"KP\"]}"),
                FinancialTransactionTestFactory.createRule("R-PEP", "PEP", RuleTypology.PEP_EXPOSURE, AlertSeverity.HIGH, "{\"minAmountThreshold\": 5000.00}")
        );

        List<FinancialTransaction> transactions = new ArrayList<>();

        // Generate 1,050 transactions:
        // 350 Round amount matches
        // 350 Geo matches
        // 350 PEP matches
        for (int i = 1; i <= 350; i++) {
            transactions.add(FinancialTransactionTestFactory.createTransaction(
                    "TXN-COMBINED-ROUND-" + i, regularAcc, new BigDecimal(i * 1000), "USD", null, null, "C-1", "US", FinancialTransactionTestFactory.BASE_TIME.plusMinutes(i), "US"
            ));
            transactions.add(FinancialTransactionTestFactory.createTransaction(
                    "TXN-COMBINED-GEO-" + i, regularAcc, new BigDecimal("250.00"), "USD", null, null, "C-2", "IR", FinancialTransactionTestFactory.BASE_TIME.plusMinutes(i), "US"
            ));
            transactions.add(FinancialTransactionTestFactory.createTransaction(
                    "TXN-COMBINED-PEP-" + i, pepAcc, new BigDecimal("10000.50"), "USD", null, null, "C-3", "US", FinancialTransactionTestFactory.BASE_TIME.plusMinutes(i), "US"
            ));
        }

        // Execute combined batch
        List<Alert> actualAlerts = ruleEngineService.evaluateBatch(transactions, activeRules);

        // Expected: 350 round + 350 geo + 350 pep = 1050 alerts
        assertEquals(1050, actualAlerts.size(), "Combined batch of 1050 transactions should produce exactly 1050 alerts");
    }
}
