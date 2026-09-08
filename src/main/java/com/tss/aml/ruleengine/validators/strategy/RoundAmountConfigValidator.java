package com.tss.aml.ruleengine.validators.strategy;

import com.tss.aml.dtos.rulesparam.RoundAmountConfigDto;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.ruleengine.validators.RuleConfigValidator;
import org.springframework.stereotype.Component;

@Component
public class RoundAmountConfigValidator implements RuleConfigValidator {

    @Override
    public RuleTypology getSupportedTypology() {
        return RuleTypology.ROUND_AMOUNT_FLAGGING;
    }

    @Override
    public Class<?> getConfigClass() {
        return RoundAmountConfigDto.class;
    }
}
