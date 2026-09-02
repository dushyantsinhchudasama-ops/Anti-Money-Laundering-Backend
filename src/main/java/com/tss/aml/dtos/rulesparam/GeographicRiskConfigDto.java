package com.tss.aml.dtos.rulesparam;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
public class GeographicRiskConfigDto {
    private List<String> highRiskCountries;
}
