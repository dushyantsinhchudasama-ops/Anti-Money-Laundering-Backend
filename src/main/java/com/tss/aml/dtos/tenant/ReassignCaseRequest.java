package com.tss.aml.dtos.tenant;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias({"newAssignedToId", "newAssigneeId"})
    private UUID newAssigneeId;

    @JsonAlias({"reassignmentNotes", "reason"})
    private String reason;
}
