package com.tss.aml.ruleengine.validators;

import com.tss.aml.enums.RuleTypology;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RuleParameterValidationService {

    private final RuleConfigValidatorRegistry registry;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public Object validate(RuleTypology typology, Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            throw new IllegalArgumentException("Parameters map cannot be null or empty");
        }

        RuleConfigValidator configValidator = registry.getValidator(typology);
        Class<?> configClass = configValidator.getConfigClass();

        Map<String, Field> declaredFields = getDeclaredFieldsMap(configClass);
        Set<String> providedKeys = parameters.keySet();

        for (String key : providedKeys) {
            if (!declaredFields.containsKey(key)) {
                throw new IllegalArgumentException("Unknown configuration parameter: parameters." + key);
            }
        }

        for (Map.Entry<String, Field> entry : declaredFields.entrySet()) {
            String fieldName = entry.getKey();
            if (!parameters.containsKey(fieldName)) {
                throw new IllegalArgumentException("Required configuration parameter is missing: parameters." + fieldName);
            }
            Object val = parameters.get(fieldName);
            if (val == null) {
                throw new IllegalArgumentException("Required configuration parameter cannot be null: parameters." + fieldName);
            }

            Class<?> expectedType = entry.getValue().getType();
            if (isNumericType(expectedType) && val instanceof String) {
                throw new IllegalArgumentException("Invalid datatype for parameters." + fieldName + ": expected numeric value");
            }
            if (List.class.isAssignableFrom(expectedType) && !(val instanceof List)) {
                throw new IllegalArgumentException("Invalid datatype for parameters." + fieldName + ": expected List");
            }
        }

        Object configObject;
        try {
            configObject = objectMapper.convertValue(parameters, configClass);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid datatype for configuration parameter: " + e.getMessage(), e);
        }

        Set<ConstraintViolation<Object>> violations = validator.validate(configObject);

        List<String> errors = violations.stream()
                .map(violation ->
                        "Invalid value for parameters."
                                + violation.getPropertyPath()
                                + ": "
                                + violation.getMessage()
                )
                .toList();

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", errors));
        }

        return configObject;
    }

    private Map<String, Field> getDeclaredFieldsMap(Class<?> clazz) {
        Map<String, Field> fieldsMap = new HashMap<>();

        for (Field field : clazz.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                fieldsMap.put(field.getName(), field);
            }
        }
        return fieldsMap;
    }

    private boolean isNumericType(Class<?> type) {
        return Number.class.isAssignableFrom(type) ||
                type.equals(int.class) ||
                type.equals(double.class) ||
                type.equals(float.class) ||
                type.equals(long.class) ||
                type.equals(BigDecimal.class) ||
                type.equals(Integer.class) ||
                type.equals(Double.class) ||
                type.equals(Float.class) ||
                type.equals(Long.class);
    }
}
