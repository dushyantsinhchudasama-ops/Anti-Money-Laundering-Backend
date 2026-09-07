package com.tss.aml.ruleengine;

import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.RuleSeverity;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.repositories.AlertRepository;
import com.tss.aml.ruleengine.evaluators.GeographicRiskRuleEvaluator;
import com.tss.aml.ruleengine.evaluators.RoundAmountEvaluator;
import com.tss.aml.ruleengine.util.RuleParameterParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleEngineConcurrencyTest {

    private RuleEngineService ruleEngineService;
    private AlertRepository alertRepository;

    @BeforeEach
    void setUp() {
        RuleParameterParser parser = new RuleParameterParser(new ObjectMapper());
        RoundAmountEvaluator roundAmountEvaluator = new RoundAmountEvaluator(parser);
        GeographicRiskRuleEvaluator geoEvaluator = new GeographicRiskRuleEvaluator(parser);

        alertRepository = Mockito.mock(AlertRepository.class);

        ruleEngineService = new RuleEngineService(
                List.of(roundAmountEvaluator, geoEvaluator),
                alertRepository
        );
        ruleEngineService.init();
    }

    @RepeatedTest(3)
    void testParallelBatchProcessing_isThreadSafeAndDeterministic() {
        int transactionCount = 1000;
        List<FinancialTransaction> transactions = new ArrayList<>(transactionCount);

        // 500 round-amount transactions (should trigger alert)
        // 500 non-round transactions (should NOT trigger alert)
        for (int i = 0; i < transactionCount; i++) {
            BigDecimal amount = (i % 2 == 0) ? new BigDecimal("10000.00") : new BigDecimal("10000.45");
            transactions.add(FinancialTransaction.builder()
                    .transactionId(UUID.randomUUID())
                    .txnNo("TXN-PARALLEL-" + i)
                    .amount(amount)
                    .currency("USD")
                    .countryCode("US")
                    .counterpartyCountryCode("US")
                    .txnTimestamp(LocalDateTime.now())
                    .build());
        }

        Rule roundRule = Rule.builder()
                .ruleId(UUID.randomUUID())
                .ruleCode("R-ROUND-01")
                .ruleName("Round Amount Flagging")
                .typology(RuleTypology.ROUND_AMOUNT_FLAGGING)
                .parameters(java.util.Map.of("moduloThreshold", 1000.00))
                .defaultSeverity(RuleSeverity.HIGH)
                .build();

        Rule geoRule = Rule.builder()
                .ruleId(UUID.randomUUID())
                .ruleCode("R-GEO-01")
                .ruleName("Geographic Risk")
                .typology(RuleTypology.GEOGRAPHIC_RISK)
                .parameters(java.util.Map.of("highRiskCountries", List.of("IR", "KP")))
                .defaultSeverity(RuleSeverity.HIGH)
                .build();

        List<Alert> alerts = ruleEngineService.evaluateBatch(transactions, List.of(roundRule, geoRule));

        // Assert exactly 500 alerts created
        assertEquals(500, alerts.size(), "Alert count must be deterministic and exactly match 500");

        // Assert no duplicate transaction references in alerts
        Set<UUID> alertedTxnIds = alerts.stream()
                .map(a -> a.getTransaction().getTransactionId())
                .collect(Collectors.toSet());
        assertEquals(500, alertedTxnIds.size(), "No duplicate transaction alerts introduced by parallel execution");
    }
}
