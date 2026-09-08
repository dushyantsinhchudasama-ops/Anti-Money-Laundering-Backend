package com.tss.aml.ruleengine.validators.strategy;

import com.tss.aml.dtos.rulesparam.PepConfigDto;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.ruleengine.validators.RuleConfigValidator;
import org.springframework.stereotype.Component;

@Component
public class PepConfigValidator implements RuleConfigValidator {

    @Override
    public RuleTypology getSupportedTypology() {
        return RuleTypology.PEP_EXPOSURE;
    }

    @Override
    public Class<?> getConfigClass() {
        return PepConfigDto.class;
    }
}
