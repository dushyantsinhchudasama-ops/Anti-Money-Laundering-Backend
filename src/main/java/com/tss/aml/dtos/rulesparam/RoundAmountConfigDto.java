package com.tss.aml.dtos.rulesparam;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RoundAmountConfigDto {
    @NotNull(message = "moduloThreshold is required")
    @Positive(message = "moduloThreshold must be positive")
    private BigDecimal moduloThreshold;
}
