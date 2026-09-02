package com.tss.aml.dtos.rulesparam;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RapidPassThroughConfigDto {
    private int windowHours;
    private BigDecimal minAmountThreshold;
    private double passThroughRatio; // e.g. 0.80 for 80% pass-through
}
