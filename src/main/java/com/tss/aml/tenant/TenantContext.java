package com.tss.aml.tenant;

public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT =
            new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCurrentTenant(String tenant) {
        if (tenant == null || tenant.isBlank()) {
            throw new IllegalArgumentException("Tenant cannot be null or blank");
        }

        CURRENT_TENANT.set(tenant);
    }

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static String getRequiredTenant() {
        String tenant = CURRENT_TENANT.get();

        if (tenant == null || tenant.isBlank()) {
            throw new IllegalStateException(
                    "No tenant is currently associated with this request"
            );
        }

        return tenant;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
