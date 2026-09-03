package com.tss.aml.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantMigrationService {

    private static final Pattern SAFE_SCHEMA_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Programmatically runs Flyway migration for a dynamically specified tenant schema.
     *
     * @param schemaName the name of the tenant schema (e.g. tenant_hdfc)
     */
    public void migrateTenantSchema(String schemaName) {
        validateSchemaName(schemaName);

        log.info("Ensuring tenant schema exists: {}", schemaName);
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);

        log.info("Executing Flyway tenant migration for schema: {}", schemaName);
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/tenant")
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .load();

        MigrateResult result = flyway.migrate();
        if (!result.success) {
            throw new RuntimeException("Flyway tenant migration failed for schema: " + schemaName);
        }
        log.info("Successfully completed Flyway tenant migration for schema: {}. Migrations executed: {}",
                schemaName, result.migrationsExecuted);
    }

    private void validateSchemaName(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalArgumentException("Schema name must not be null or blank");
        }
        if (!SAFE_SCHEMA_PATTERN.matcher(schemaName).matches()) {
            throw new IllegalArgumentException("Invalid schema name: " + schemaName + ". Schema name must contain only alphanumeric characters and underscores.");
        }
    }
}
