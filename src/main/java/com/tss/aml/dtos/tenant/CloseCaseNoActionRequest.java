package com.tss.aml.dtos.tenant;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloseCaseNoActionRequest {

    @NotBlank(message = "Rationale is required for no-action closure")
    private String rationale;
}
