package com.tss.aml.dtos.tenant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceOfficerWorkloadResponse {
    private UUID userId;
    private String userCode;
    private String firstName;
    private String lastName;
    private String email;
    private String employeeId;
    private Boolean isActive;
    private long activeCaseCount;
}
