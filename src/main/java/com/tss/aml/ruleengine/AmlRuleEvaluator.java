package com.tss.aml.ruleengine;


import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.RuleTypology;

public interface AmlRuleEvaluator {
    boolean evaluate(FinancialTransaction financialTransaction, String params);
    RuleTypology getSupportedTypology();
}
