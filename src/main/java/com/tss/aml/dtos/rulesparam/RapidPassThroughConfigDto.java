package com.tss.aml.dtos.rulesparam;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RapidPassThroughConfigDto {
    @NotNull(message = "windowHours is required")
    @Positive(message = "windowHours must be positive")
    private Integer windowHours;

    @NotNull(message = "minAmountThreshold is required")
    @Positive(message = "minAmountThreshold must be positive")
    private BigDecimal minAmountThreshold;

    @NotNull(message = "passThroughRatio is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "passThroughRatio must be greater than 0")
    @DecimalMax(value = "1.0", inclusive = true, message = "passThroughRatio cannot exceed 1.0")
    private Double passThroughRatio;
}
