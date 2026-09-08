package com.tss.aml.ruleengine.validators.strategy;

import com.tss.aml.dtos.rulesparam.StructuringConfigDto;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.ruleengine.validators.RuleConfigValidator;
import org.springframework.stereotype.Component;

@Component
public class StructuringConfigValidator implements RuleConfigValidator {

    @Override
    public RuleTypology getSupportedTypology() {
        return RuleTypology.STRUCTURING_SMURFING;
    }

    @Override
    public Class<?> getConfigClass() {
        return StructuringConfigDto.class;
    }
}
