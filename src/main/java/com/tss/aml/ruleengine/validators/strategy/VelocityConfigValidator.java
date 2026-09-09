package com.tss.aml.ruleengine.validators.strategy;

import com.tss.aml.dtos.rulesparam.VelocityConfigDto;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.ruleengine.validators.RuleConfigValidator;
import org.springframework.stereotype.Component;

@Component
public class VelocityConfigValidator implements RuleConfigValidator {

    @Override
    public RuleTypology getSupportedTypology() {
        return RuleTypology.VELOCITY_CHECK;
    }

    @Override
    public Class<?> getConfigClass() {
        return VelocityConfigDto.class;
    }
}
