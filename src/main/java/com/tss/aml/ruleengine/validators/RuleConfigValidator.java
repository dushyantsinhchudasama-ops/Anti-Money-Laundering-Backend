package com.tss.aml.ruleengine.validators;

import com.tss.aml.enums.RuleTypology;

public interface RuleConfigValidator {
    RuleTypology getSupportedTypology();
    Class<?> getConfigClass();
}
