package com.tss.aml.dtos.tenant;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCaseRequest {

    @NotEmpty(message = "At least one alert ID must be provided")
    private List<UUID> alertIds;

    @NotNull(message = "Assignee user ID must be specified")
    private UUID assigneeId;

    private String initialNote;
}
