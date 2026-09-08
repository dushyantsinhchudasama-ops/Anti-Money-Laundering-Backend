package com.tss.aml.dtos.tenant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceOfficerDashboardResponse {
    private long totalAssignedCases;
    private long openCasesCount;
    private long inProgressCasesCount;
    private long escalatedCasesCount;
    private long closedCasesCount;
    private long relatedAlertsCount;
}
