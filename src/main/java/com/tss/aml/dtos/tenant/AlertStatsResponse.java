package com.tss.aml.dtos.tenant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertStatsResponse {
    private long highSeverityCount;
    private long mediumSeverityCount;
    private long lowSeverityCount;
    private long openAlertsCount;
    private long assignedAlertsCount;
    private long closedAlertsCount;
    private long totalAlertsCount;
}
