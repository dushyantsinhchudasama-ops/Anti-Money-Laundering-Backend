package com.tss.aml.dtos.tenant;

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
public class ReassignCaseRequest {

    @NotNull(message = "New assignee user ID must be specified")
    private UUID newAssigneeId;

    private String reason;
}
