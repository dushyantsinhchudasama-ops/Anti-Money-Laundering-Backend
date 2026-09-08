package com.tss.aml.ruleengine.validators.strategy;

import com.tss.aml.dtos.rulesparam.DormantAccountConfigDto;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.ruleengine.validators.RuleConfigValidator;
import org.springframework.stereotype.Component;

@Component
public class DormantAccountConfigValidator implements RuleConfigValidator {

    @Override
    public RuleTypology getSupportedTypology() {
        return RuleTypology.DORMANT_ACCOUNT;
    }

    @Override
    public Class<?> getConfigClass() {
        return DormantAccountConfigDto.class;
    }
}
