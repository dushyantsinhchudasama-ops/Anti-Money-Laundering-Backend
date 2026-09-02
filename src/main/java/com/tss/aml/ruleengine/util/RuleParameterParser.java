package com.tss.aml.ruleengine.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class RuleParameterParser {
    private final ObjectMapper objectMapper;

    public <T> T parse(String params, Class<T> type){
        try {
            return objectMapper.readValue(params, type);
        }catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
