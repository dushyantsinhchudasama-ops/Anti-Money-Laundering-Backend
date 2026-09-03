package com.tss.aml.dtos.tenant;

import com.tss.aml.enums.TenantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTenantResponse {

    private UUID tenantId;
    private String tenantCode;
    private String tenantName;
    private String displayName;
    private String schemaName;
    private TenantStatus status;
    private UUID onboardedByAdminId;
    private LocalDateTime createdAt;
}
