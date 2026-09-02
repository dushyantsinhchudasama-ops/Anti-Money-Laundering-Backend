package com.tss.aml.config;

import com.tss.aml.tenant.TenantConnectionProvider;
import com.tss.aml.tenant.TenantIdentifierResolver;
import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class HibernateMultitenancyConfig {

    @Bean
        public HibernatePropertiesCustomizer hibernatePropertiesCustomizer(
            TenantConnectionProvider tenantConnectionProvider,
            TenantIdentifierResolver tenantIdentifierResolver
    ) {
        return properties -> properties.putAll(Map.of(
                MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, tenantConnectionProvider,
                MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver
        ));
    }
}
