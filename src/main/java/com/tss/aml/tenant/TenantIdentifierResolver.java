package com.tss.aml.tenant;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Resolves the current tenant schema identifier from {@link TenantContext}.
 *
 * <p><strong>Fallback Behavior:</strong>
 * If no tenant is present in {@link TenantContext} (e.g. during Hibernate bootstrap/startup
 * or system-level unauthenticated initialization), this resolver falls back to {@code "public"}.
 *
 * <p>System-level entities (e.g. {@code Tenant}, {@code Users}, {@code SystemAdmin}) explicitly
 * specify {@code schema = "public"} in their {@code @Table} mapping, ensuring they always target
 * the public schema regardless of the active tenant context.
 *
 * <p>Tenant-scoped entities (e.g. {@code TransactionBatch}, {@code Alert}, {@code FinancialTransaction})
 * do not specify a schema attribute, so Hibernate prepends the schema returned by this resolver
 * or relies on the connection schema set by {@link TenantConnectionProvider}.
 */
@Component
@Slf4j
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<String> {

    /**
     * Default schema fallback used during Hibernate bootstrap or non-tenant execution context.
     */
    private static final String DEFAULT_TENANT = "public";

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenant = TenantContext.getCurrentTenant();

        if (tenant == null || tenant.isBlank()) {
            log.trace("TenantContext is empty; defaulting to public schema for Hibernate/system operation");
            return DEFAULT_TENANT;
        }

        return tenant;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}