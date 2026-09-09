package com.tss.aml.ruleengine.validators.strategy;

import com.tss.aml.dtos.rulesparam.GeographicRiskConfigDto;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.ruleengine.validators.RuleConfigValidator;
import org.springframework.stereotype.Component;

@Component
public class GeographicRiskConfigValidator implements RuleConfigValidator {

    @Override
    public RuleTypology getSupportedTypology() {
        return RuleTypology.GEOGRAPHIC_RISK;
    }

    @Override
    public Class<?> getConfigClass() {
        return GeographicRiskConfigDto.class;
    }
}
