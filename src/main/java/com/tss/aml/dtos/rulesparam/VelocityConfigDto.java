package com.tss.aml.dtos.rulesparam;

import lombok.Data;

@Data
public class VelocityConfigDto {
    private int windowDays;
    private int maxTransactionCount;
}
