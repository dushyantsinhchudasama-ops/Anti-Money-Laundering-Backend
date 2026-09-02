package com.tss.aml.tenant;

import com.tss.aml.entities.system.Tenant;
import com.tss.aml.enums.TenantStatus;
import com.tss.aml.repositories.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    public Tenant getTenant(UUID tenantId) {

        return tenantRepository.findById(tenantId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Tenant not found: " + tenantId
                        )
                );
    }

    public String getSchemaName(UUID tenantId) {

        Tenant tenant = getTenant(tenantId);

                if (tenant.getStatus() != TenantStatus.ACTIVE) {
                        throw new IllegalStateException(
                                        "Tenant is not active: " + tenantId
                        );
                }

        if (tenant.getSchemaName() == null ||
                tenant.getSchemaName().isBlank()) {

            throw new IllegalStateException(
                    "Tenant does not have a configured schema"
            );
        }

        return tenant.getSchemaName();
    }
}
