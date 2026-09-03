package com.tss.aml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PublicFlywayMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Verify Flyway schema history table exists in public schema")
    void publicSchemaFlywayHistoryTableExists() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'flyway_schema_history'",
                Integer.class
        );
        assertThat(tableCount).isNotNull().isEqualTo(1);
    }

    @Test
    @DisplayName("Verify Flyway schema history table records successful execution of V1__init_system_schema.sql")
    void publicSchemaFlywayHistoryContainsV1MigrationRecord() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.flyway_schema_history WHERE version = '1' AND success = true",
                Integer.class
        );
        assertThat(migrationCount).isNotNull().isGreaterThanOrEqualTo(1);
    }


    @Test
    @DisplayName("Verify all 7 system tables exist in public schema following Flyway migration execution")
    void allPublicSystemTablesExistInPublicSchema() {
        List<String> requiredSystemTables = List.of(
                "system_admin",
                "tenants",
                "users",
                "rules",
                "rule_version_history",
                "bank_rule_assignment",
                "system_audit_log"
        );

        for (String tableName : requiredSystemTables) {
            Integer tableCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class,
                    tableName
            );
            assertThat(tableCount)
                    .withFailMessage("Expected system table '%s' to exist in public schema", tableName)
                    .isNotNull()
                    .isEqualTo(1);
        }
    }
}
