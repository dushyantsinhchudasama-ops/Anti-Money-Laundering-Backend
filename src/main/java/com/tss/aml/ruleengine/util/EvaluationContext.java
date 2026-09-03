package com.tss.aml.ruleengine.util;

import com.tss.aml.entities.tenant.FinancialTransaction;
import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
public class EvaluationContext {
    private FinancialTransaction currentTransaction;
    private String currentRuleParameters;

    private final Map<UUID, BigDecimal> accountHistoricalSums = new HashMap<>();
}
