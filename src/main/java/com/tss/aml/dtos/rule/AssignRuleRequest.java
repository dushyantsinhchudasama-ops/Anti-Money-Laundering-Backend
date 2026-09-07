package com.tss.aml.dtos.rule;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignRuleRequest {
    @NotNull(message = "tenantId is required")
    private UUID tenantId;
}
