package com.tss.aml.dtos.rule;

import com.tss.aml.enums.RuleSeverity;
import com.tss.aml.enums.RuleTypology;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@Data
@RequiredArgsConstructor
public class UpdateRuleRequest {
    @NotBlank(message = "Rule name cannot be blank")
    @Size(min = 3, max = 100, message = "Rule name must be between 3 and 100 characters")
    private String ruleName;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @NotNull(message = "Rule typology is required")
    private RuleTypology typology;

    @NotNull(message = "Default severity is required")
    private RuleSeverity defaultSeverity;

    @NotNull(message = "Parameters map cannot be null")
    @Size(min = 1, message = "Parameters cannot be empty")
    private Map<String, Object> parameters;
}
