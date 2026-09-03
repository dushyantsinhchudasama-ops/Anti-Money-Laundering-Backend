package com.tss.aml.dtos.rulesparam;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Data
@RequiredArgsConstructor
public class RoundAmountConfigDto {
    private BigDecimal moduloThreshold;
}
