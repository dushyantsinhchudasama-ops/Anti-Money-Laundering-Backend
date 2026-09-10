package com.tss.aml.dtos.tenant;

import com.tss.aml.enums.TenantStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTenantStatusRequest {

    @NotNull(message = "Tenant status is required")
    private TenantStatus status;
}
