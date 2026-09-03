package com.tss.aml.dtos.rulesparam;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StructuringConfigDto {
    private int windowDays;
    private BigDecimal reportingThreshold;
}
