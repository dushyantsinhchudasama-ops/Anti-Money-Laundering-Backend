package com.tss.aml.dtos.rulesparam;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DormantAccountConfigDto {
    private int dormantDays;
    private BigDecimal minAmountThreshold;
}
