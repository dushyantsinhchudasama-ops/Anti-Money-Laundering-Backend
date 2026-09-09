package com.tss.aml.dtos.rulesparam;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class GeographicRiskConfigDto {
    @NotNull(message = "highRiskCountries is required")
    @NotEmpty(message = "highRiskCountries list cannot be empty")
    private List<String> highRiskCountries;
}
