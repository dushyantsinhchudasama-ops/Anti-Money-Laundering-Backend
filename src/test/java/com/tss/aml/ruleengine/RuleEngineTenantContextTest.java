package com.tss.aml.ruleengine;

import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.RuleSeverity;
import com.tss.aml.enums.RuleStatus;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.repositories.AlertRepository;
import com.tss.aml.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class RuleEngineTenantContextTest {

    private AlertRepository alertRepository;
    private AmlRuleEvaluator mockEvaluator;
    private RuleEngineService ruleEngineService;

    @BeforeEach
    void setUp() {
        alertRepository = Mockito.mock(AlertRepository.class);
        mockEvaluator = Mockito.mock(AmlRuleEvaluator.class);
        when(mockEvaluator.getSupportedTypology()).thenReturn(RuleTypology.GEOGRAPHIC_RISK);

        ruleEngineService = new RuleEngineService(List.of(mockEvaluator), alertRepository);
        ruleEngineService.init();
    }

    @Test
    @DisplayName("evaluateBatch preserves TenantContext on executing thread during evaluation")
    void evaluateBatchPreservesTenantContext() {
        TenantContext.setCurrentTenant("tenant_acme");

        Rule rule = Rule.builder()
                .ruleId(UUID.randomUUID())
                .ruleCode("GEO-001")
                .ruleName("Geographic Risk")
                .typology(RuleTypology.GEOGRAPHIC_RISK)
                .defaultSeverity(RuleSeverity.HIGH)
                .status(RuleStatus.ACTIVE)
                .parameters(Collections.emptyMap())
                .build();

        FinancialTransaction txn = FinancialTransaction.builder()
                .transactionId(UUID.randomUUID())
                .countryCode("IR")
                .build();

        when(mockEvaluator.evaluate(any(), any())).thenAnswer(invocation -> {
            // Verify TenantContext is still set on the evaluator thread
            assertThat(TenantContext.getCurrentTenant()).isEqualTo("tenant_acme");
            return false;
        });

        try {
            ruleEngineService.evaluateBatch(List.of(txn), List.of(rule));
        } finally {
            TenantContext.clear();
        }
    }
}
