package com.tss.aml.ruleengine;

import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.tenant.Account;
import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.RuleSeverity;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.repositories.AlertRepository;
import com.tss.aml.repositories.FinancialTransactionRepository;
import com.tss.aml.ruleengine.evaluators.DormantAccountEvaluator;
import com.tss.aml.ruleengine.evaluators.StructuringEvaluator;
import com.tss.aml.ruleengine.evaluators.VelocityEvaluator;
import com.tss.aml.ruleengine.util.RuleParameterParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistoricalTimeBoundaryTest {

    private FinancialTransactionRepository transactionRepository;
    private RuleEngineService ruleEngineService;

    @BeforeEach
    void setUp() {
        RuleParameterParser parser = new RuleParameterParser(new ObjectMapper());
        transactionRepository = Mockito.mock(FinancialTransactionRepository.class);
        AlertRepository alertRepository = Mockito.mock(AlertRepository.class);

        VelocityEvaluator velocityEvaluator = new VelocityEvaluator(parser, transactionRepository);
        StructuringEvaluator structuringEvaluator = new StructuringEvaluator(parser, transactionRepository);
        DormantAccountEvaluator dormantEvaluator = new DormantAccountEvaluator(parser, transactionRepository);

        ruleEngineService = new RuleEngineService(
                List.of(velocityEvaluator, structuringEvaluator, dormantEvaluator),
                alertRepository
        );
        ruleEngineService.init();
    }

    @Test
    void testVelocityEvaluator_doesNotCountCurrentTransactionAtUpperBoundary() {
        Account account = Account.builder()
                .accountId(UUID.randomUUID())
                .accountNumber("ACC-VELOCITY-01")
                .build();

        // Window: 1 day lookback
        // Current Txn time: 2026-09-02T11:00:00
        LocalDateTime currentTxnTime = LocalDateTime.of(2026, 9, 2, 11, 0, 0);
        LocalDateTime windowStart = currentTxnTime.minusDays(1); // 2026-09-01T11:00:00

        FinancialTransaction currentTxn = FinancialTransaction.builder()
                .transactionId(UUID.randomUUID())
                .originatorAccount(account)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .countryCode("US")
                .txnTimestamp(currentTxnTime)
                .build();

        Rule velocityRule = Rule.builder()
                .ruleId(UUID.randomUUID())
                .ruleCode("R-VELOCITY-01")
                .ruleName("Velocity Check")
                .typology(RuleTypology.VELOCITY_CHECK)
                .parameters(java.util.Map.of("windowDays", 1, "maxTransactionCount", 5))
                .defaultSeverity(RuleSeverity.HIGH)
                .build();

        when(transactionRepository.countTransactionsInWindow(eq(account), eq(windowStart), eq(currentTxnTime)))
                .thenReturn(6);

        List<Alert> alerts = ruleEngineService.evaluateBatch(List.of(currentTxn), List.of(velocityRule));

        assertEquals(1, alerts.size());

        // Capture exact arguments passed to repository
        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(transactionRepository).countTransactionsInWindow(eq(account), startCaptor.capture(), endCaptor.capture());

        assertEquals(windowStart, startCaptor.getValue(), "Start boundary must be windowStart (10:00)");
        assertEquals(currentTxnTime, endCaptor.getValue(), "End boundary must be current transaction timestamp");
    }

    @Test
    void testDormantAccountEvaluator_excludesCurrentTransactionInPriorCount() {
        Account account = Account.builder()
                .accountId(UUID.randomUUID())
                .accountNumber("ACC-DORMANT-02")
                .build();

        LocalDateTime currentTxnTime = LocalDateTime.of(2026, 9, 2, 11, 0, 0);
        LocalDateTime dormantStart = currentTxnTime.minusDays(90);

        FinancialTransaction currentTxn = FinancialTransaction.builder()
                .transactionId(UUID.randomUUID())
                .originatorAccount(account)
                .amount(new BigDecimal("50000.00"))
                .currency("USD")
                .countryCode("US")
                .txnTimestamp(currentTxnTime)
                .build();

        Rule dormantRule = Rule.builder()
                .ruleId(UUID.randomUUID())
                .ruleCode("R-DORMANT-01")
                .ruleName("Dormant Account Reactivation")
                .typology(RuleTypology.DORMANT_ACCOUNT)
                .parameters(java.util.Map.of("dormantDays", 90, "minAmountThreshold", 10000.00))
                .defaultSeverity(RuleSeverity.HIGH)
                .build();

        // 0 prior transactions before currentTxnTime
        when(transactionRepository.countPriorTransactions(eq(account), eq(dormantStart), eq(currentTxnTime)))
                .thenReturn(0);

        List<Alert> alerts = ruleEngineService.evaluateBatch(List.of(currentTxn), List.of(dormantRule));

        assertEquals(1, alerts.size());
        verify(transactionRepository).countPriorTransactions(eq(account), eq(dormantStart), eq(currentTxnTime));
    }
}
