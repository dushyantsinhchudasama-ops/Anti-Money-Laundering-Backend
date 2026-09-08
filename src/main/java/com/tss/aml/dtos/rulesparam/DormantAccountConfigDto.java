package com.tss.aml.dtos.rulesparam;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DormantAccountConfigDto {
    @NotNull(message = "dormantDays is required")
    @Positive(message = "dormantDays must be positive")
    private Integer dormantDays;

    @NotNull(message = "minAmountThreshold is required")
    @Positive(message = "minAmountThreshold must be positive")
    private BigDecimal minAmountThreshold;
}
