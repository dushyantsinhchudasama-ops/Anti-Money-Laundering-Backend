package com.tss.aml.dtos.rulesparam;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class VelocityConfigDto {
    @NotNull(message = "windowDays is required")
    @Positive(message = "windowDays must be positive")
    private Integer windowDays;

    @NotNull(message = "maxTransactionCount is required")
    @Positive(message = "maxTransactionCount must be positive")
    private Integer maxTransactionCount;
}
