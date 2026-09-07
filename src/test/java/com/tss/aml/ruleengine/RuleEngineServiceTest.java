package com.tss.aml.ruleengine;

import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.tenant.Account;
import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.AlertStatus;
import com.tss.aml.enums.RiskRating;
import com.tss.aml.enums.RuleSeverity;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.repositories.AlertRepository;
import com.tss.aml.repositories.FinancialTransactionRepository;
import com.tss.aml.ruleengine.evaluators.*;
import com.tss.aml.ruleengine.util.RuleParameterParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuleEngineServiceTest {

    private RuleEngineService ruleEngineService;
    private AlertRepository alertRepository;
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

        alertRepository = Mockito.mock(AlertRepository.class);

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
    void testRoundAmountMatch_generatesAlert() {
        Rule roundAmountRule = Rule.builder()
                .ruleId(UUID.randomUUID())
                .ruleCode("R-ROUND-01")
                .ruleName("Round Amount Flagging")
                .typology(RuleTypology.ROUND_AMOUNT_FLAGGING)
                .parameters(java.util.Map.of("moduloThreshold", 1000.00))
                .defaultSeverity(RuleSeverity.MEDIUM)
                .build();

        FinancialTransaction txn = FinancialTransaction.builder()
                .transactionId(UUID.randomUUID())
                .txnNo("TXN-001")
                .amount(new BigDecimal("50000.00"))
                .currency("USD")
                .countryCode("US")
                .txnTimestamp(LocalDateTime.now())
                .build();

        List<Alert> alerts = ruleEngineService.evaluateBatch(List.of(txn), List.of(roundAmountRule));

        assertEquals(1, alerts.size());
        Alert alert = alerts.get(0);
        assertEquals(AlertSeverity.MEDIUM, alert.getSeverity());
        assertEquals(AlertStatus.OPEN, alert.getAlertStatus());
        verify(alertRepository).saveAll(anyList());
    }

    @Test
    void testPepMatch_generatesAlert() {
        Account pepAccount = Account.builder()
                .accountId(UUID.randomUUID())
                .accountNumber("ACC-PEP-01")
                .accountHolderName("High Public Official")
                .riskRating(RiskRating.HIGH)
                .countryCode("US")
                .build();

        Rule pepRule = Rule.builder()
                .ruleId(UUID.randomUUID())
                .ruleCode("R-PEP-01")
                .ruleName("PEP High Value Transaction")
                .typology(RuleTypology.PEP_EXPOSURE)
                .parameters(java.util.Map.of("minAmountThreshold", 5000.00))
                .defaultSeverity(RuleSeverity.HIGH)
                .build();

        FinancialTransaction txn = FinancialTransaction.builder()
                .transactionId(UUID.randomUUID())
                .originatorAccount(pepAccount)
                .amount(new BigDecimal("10000.00"))
                .currency("USD")
                .countryCode("US")
                .txnTimestamp(LocalDateTime.now())
                .build();

        List<Alert> alerts = ruleEngineService.evaluateBatch(List.of(txn), List.of(pepRule));

        assertEquals(1, alerts.size());
        assertEquals(AlertSeverity.HIGH, alerts.get(0).getSeverity());
    }

    @Test
    void testDormantAccountReactivation_generatesAlert() {
        Account dormantAcc = Account.builder()
                .accountId(UUID.randomUUID())
                .accountNumber("ACC-DORMANT-01")
                .riskRating(RiskRating.LOW)
                .countryCode("US")
                .build();

        Rule dormantRule = Rule.builder()
                .ruleId(UUID.randomUUID())
                .ruleCode("R-DORMANT-01")
                .ruleName("Dormant Account Reactivation")
                .typology(RuleTypology.DORMANT_ACCOUNT)
                .parameters(java.util.Map.of("dormantDays", 90, "minAmountThreshold", 10000.00))
                .defaultSeverity(RuleSeverity.HIGH)
                .build();

        when(transactionRepository.countPriorTransactions(any(), any(), any())).thenReturn(0);

        FinancialTransaction txn = FinancialTransaction.builder()
                .transactionId(UUID.randomUUID())
                .originatorAccount(dormantAcc)
                .amount(new BigDecimal("25000.00"))
                .currency("USD")
                .countryCode("US")
                .txnTimestamp(LocalDateTime.now())
                .build();

        List<Alert> alerts = ruleEngineService.evaluateBatch(List.of(txn), List.of(dormantRule));

        assertEquals(1, alerts.size());
        assertEquals(AlertSeverity.HIGH, alerts.get(0).getSeverity());
    }
}
