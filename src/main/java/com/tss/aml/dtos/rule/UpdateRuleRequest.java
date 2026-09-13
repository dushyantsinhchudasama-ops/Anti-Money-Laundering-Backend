package com.tss.aml.dtos.rule;

import com.tss.aml.enums.RuleSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRuleRequest {
    @NotBlank(message = "Rule name cannot be blank")
    @Size(min = 3, max = 100, message = "Rule name must be between 3 and 100 characters")
    private String ruleName;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @NotNull(message = "Default severity is required")
    private RuleSeverity defaultSeverity;

    @NotNull(message = "Parameters map cannot be null")
    @Size(min = 1, message = "Parameters cannot be empty")
    private Map<String, Object> parameters;
}
