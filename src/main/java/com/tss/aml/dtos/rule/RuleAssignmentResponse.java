package com.tss.aml.dtos.rule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleAssignmentResponse {
    private UUID assignmentId;
    private UUID tenantId;
    private String tenantCode;
    private String tenantName;
    private UUID ruleId;
    private String ruleCode;
    private String ruleName;
    private UUID assignedByAdminId;
    private String assignedByEmail;
    private LocalDateTime assignedAt;
}
