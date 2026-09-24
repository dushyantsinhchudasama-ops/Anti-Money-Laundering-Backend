package com.tss.aml.tenant;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<String> {


    private static final String DEFAULT_TENANT = "public";

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenant = TenantContext.getCurrentTenant();

        if (tenant == null || tenant.isBlank()) {
            log.trace("TenantContext is empty");
            return DEFAULT_TENANT;
        }

        return tenant;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}