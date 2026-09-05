package com.tss.aml.dtos.rule;

import com.tss.aml.enums.RuleSeverity;
import com.tss.aml.enums.RuleStatus;
import com.tss.aml.enums.RuleTypology;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@RequiredArgsConstructor
@Builder
public class CreateRuleResponse {
    private UUID ruleId;
    private String ruleCode;
    private String ruleName;
    private String description;
    private RuleTypology typology;
    private RuleSeverity defaultSeverity;
    private boolean isActive;
    private RuleStatus status;
    private Map<String, Object> parameters;
}
