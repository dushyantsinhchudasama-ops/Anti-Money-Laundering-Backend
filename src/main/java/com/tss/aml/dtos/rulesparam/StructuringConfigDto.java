package com.tss.aml.dtos.rulesparam;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StructuringConfigDto {
    @NotNull(message = "windowDays is required")
    @Positive(message = "windowDays must be positive")
    private Integer windowDays;

    @NotNull(message = "reportingThreshold is required")
    @Positive(message = "reportingThreshold must be positive")
    private BigDecimal reportingThreshold;
}
