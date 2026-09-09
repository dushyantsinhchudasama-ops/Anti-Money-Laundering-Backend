package com.tss.aml.ruleengine.validators;

import com.tss.aml.enums.RuleTypology;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RuleConfigValidatorRegistry {

    private final Map<RuleTypology, RuleConfigValidator> validators;

    public RuleConfigValidatorRegistry(List<RuleConfigValidator> validatorList) {
        this.validators = validatorList.stream()
                .collect(Collectors.toUnmodifiableMap(
                        RuleConfigValidator::getSupportedTypology,
                        Function.identity()
                ));
    }

    public RuleConfigValidator getValidator(RuleTypology typology) {
        RuleConfigValidator validator = validators.get(typology);
        if (validator == null) {
            throw new IllegalArgumentException("No configuration validator registered for typology: " + typology);
        }
        return validator;
    }
}
