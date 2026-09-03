package com.tss.aml;

import com.tss.aml.services.TenantMigrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TenantMigrationServiceIntegrationTest {

    @Autowired
    private TenantMigrationService tenantMigrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTestSchemas() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS tenant_hdfc CASCADE");
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS tenant_icici CASCADE");
    }

    private static final List<String> REQUIRED_TENANT_TABLES = List.of(

            "account",
            "transaction_batch",
            "financial_transaction",
            "batch_validation_error",
            "aml_case",
            "alerts",
            "case_note",
            "escalation",
            "sar_str",
            "audit_log",
            "notification"
    );

    @Test
    @DisplayName("Verify tenant_hdfc schema creation and dynamic Flyway migration")
    void migrateTenantHdfcSchemaSuccessfully() {
        String schemaName = "tenant_hdfc";

        tenantMigrationService.migrateTenantSchema(schemaName);

        verifySchemaExists(schemaName);
        verifyTenantTablesExist(schemaName);
        verifyFlywayHistoryExists(schemaName);
    }

    @Test
    @DisplayName("Verify tenant_icici schema creation and independent dynamic Flyway migration")
    void migrateTenantIciciSchemaIndependently() {
        String schemaName = "tenant_icici";

        tenantMigrationService.migrateTenantSchema(schemaName);

        verifySchemaExists(schemaName);
        verifyTenantTablesExist(schemaName);
        verifyFlywayHistoryExists(schemaName);
    }

    @Test
    @DisplayName("Verify rejection of invalid schema names to prevent SQL injection")
    void rejectsInvalidSchemaNames() {
        assertThatThrownBy(() -> tenantMigrationService.migrateTenantSchema(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> tenantMigrationService.migrateTenantSchema(""))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> tenantMigrationService.migrateTenantSchema("tenant_hdfc; DROP TABLE public.users;"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void verifySchemaExists(String schemaName) {
        Integer schemaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class,
                schemaName
        );
        assertThat(schemaCount)
                .withFailMessage("Expected schema '%s' to exist", schemaName)
                .isNotNull()
                .isEqualTo(1);
    }

    private void verifyTenantTablesExist(String schemaName) {
        for (String tableName : REQUIRED_TENANT_TABLES) {
            Integer tableCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?",
                    Integer.class,
                    schemaName,
                    tableName
            );
            assertThat(tableCount)
                    .withFailMessage("Expected table '%s' to exist in schema '%s'", tableName, schemaName)
                    .isNotNull()
                    .isEqualTo(1);
        }
    }

    private void verifyFlywayHistoryExists(String schemaName) {
        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'flyway_schema_history'",
                Integer.class,
                schemaName
        );
        assertThat(historyCount)
                .withFailMessage("Expected flyway_schema_history table in schema '%s'", schemaName)
                .isNotNull()
                .isEqualTo(1);

        String sql = String.format("SELECT COUNT(*) FROM %s.flyway_schema_history WHERE version = '1' AND success = true", schemaName);
        Integer recordCount = jdbcTemplate.queryForObject(sql, Integer.class);
        assertThat(recordCount)
                .withFailMessage("Expected migration version 1 record in %s.flyway_schema_history", schemaName)
                .isNotNull()
                .isGreaterThanOrEqualTo(1);
    }
}
