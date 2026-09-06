package com.tss.aml.dtos.tenant;

import com.tss.aml.enums.UserRole;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter
@Builder
public class ComplianceOfficerResponse {
    private UUID userId;
    private String userCode;
    private UUID tenantId;
    private String employeeId;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private UserRole role;
    private Boolean isActive;
    private Boolean mustResetPassword;
    private String temporaryPassword;
    private LocalDateTime createdAt;
}
