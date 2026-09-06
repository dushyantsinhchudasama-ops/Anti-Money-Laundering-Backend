package com.tss.aml.ruleengine.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class RuleParameterParser {
    private final ObjectMapper objectMapper;

    public <T> T parse(Object params, Class<T> type) {
        try {
            if (params == null) return null;
            if (params instanceof String s) {
                return objectMapper.readValue(s, type);
            }
            return objectMapper.convertValue(params, type);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
