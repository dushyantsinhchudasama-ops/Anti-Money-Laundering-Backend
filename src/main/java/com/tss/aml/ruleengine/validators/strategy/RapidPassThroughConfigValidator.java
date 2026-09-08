package com.tss.aml.ruleengine.validators.strategy;

import com.tss.aml.dtos.rulesparam.RapidPassThroughConfigDto;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.ruleengine.validators.RuleConfigValidator;
import org.springframework.stereotype.Component;

@Component
public class RapidPassThroughConfigValidator implements RuleConfigValidator {

    @Override
    public RuleTypology getSupportedTypology() {
        return RuleTypology.RAPID_PASS_THROUGH;
    }

    @Override
    public Class<?> getConfigClass() {
        return RapidPassThroughConfigDto.class;
    }
}
