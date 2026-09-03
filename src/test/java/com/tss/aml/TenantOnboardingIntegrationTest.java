package com.tss.aml;

import com.tss.aml.dtos.tenant.CreateTenantRequest;
import com.tss.aml.dtos.tenant.CreateTenantResponse;
import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.entities.system.Tenant;
import com.tss.aml.enums.TenantStatus;
import com.tss.aml.repositories.SystemAdminRepository;
import com.tss.aml.repositories.TenantRepository;
import com.tss.aml.services.TenantMigrationService;
import com.tss.aml.tenant.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class TenantOnboardingIntegrationTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private SystemAdminRepository systemAdminRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private TenantMigrationService tenantMigrationService;

    private SystemAdmin testAdmin;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS tenant_axis CASCADE");
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS tenant_failbank CASCADE");

        tenantRepository.findByTenantCode("AXIS").ifPresent(tenantRepository::delete);
        tenantRepository.findByTenantCode("FAILBANK").ifPresent(tenantRepository::delete);

        testAdmin = systemAdminRepository.findBySystemAdminCode("ADM001")
                .orElseGet(() -> {
                    SystemAdmin admin = new SystemAdmin();
                    admin.setSystemAdminCode("ADM001");
                    admin.setFirstName("System");
                    admin.setLastName("Admin");
                    admin.setPhoneNumber("1234567890");
                    admin.setEmail("sysadmin@aml.com");
                    admin.setPasswordHash("hashedpassword");
                    admin.setIsActive(true);
                    return systemAdminRepository.save(admin);
                });

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                testAdmin,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Verify successful tenant onboarding flow: DB record created, schema created, migration executed, status ACTIVE")
    void successfulTenantOnboardingFlow() {
        CreateTenantRequest request = CreateTenantRequest.builder()
                .tenantCode("AXIS")
                .tenantName("Axis Bank Ltd")
                .displayName("Axis Bank")
                .build();

        CreateTenantResponse response = tenantService.onboardTenant(request);

        assertThat(response).isNotNull();
        assertThat(response.getTenantCode()).isEqualTo("AXIS");
        assertThat(response.getSchemaName()).isEqualTo("tenant_axis");
        assertThat(response.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(response.getOnboardedByAdminId()).isEqualTo(testAdmin.getSystemAdminId());

        // Verify public DB tenant record
        Tenant tenantInDb = tenantRepository.findByTenantCode("AXIS").orElseThrow();
        assertThat(tenantInDb.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenantInDb.getOnboardedByAdmin().getSystemAdminId()).isEqualTo(testAdmin.getSystemAdminId());

        // Verify schema exists
        Integer schemaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'tenant_axis'",
                Integer.class
        );
        assertThat(schemaCount).isEqualTo(1);

        // Verify tenant tables exist inside tenant_axis
        List<String> requiredTables = List.of(
                "account", "transaction_batch", "financial_transaction",
                "batch_validation_error", "aml_case", "alerts", "case_note",
                "escalation", "sar_str", "audit_log", "notification", "flyway_schema_history"
        );
        for (String tableName : requiredTables) {
            Integer tableCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'tenant_axis' AND table_name = ?",
                    Integer.class,
                    tableName
            );
            assertThat(tableCount)
                    .withFailMessage("Expected table '%s' to exist in tenant_axis", tableName)
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Verify duplicate tenant code is rejected during onboarding")
    void duplicateTenantCodeIsRejected() {
        CreateTenantRequest request1 = CreateTenantRequest.builder()
                .tenantCode("AXIS")
                .tenantName("Axis Bank")
                .displayName("Axis Bank")
                .build();

        if (!tenantRepository.existsByTenantCode("AXIS")) {
            tenantService.onboardTenant(request1);
        }

        CreateTenantRequest duplicateRequest = CreateTenantRequest.builder()
                .tenantCode("AXIS")
                .tenantName("Another Axis Bank")
                .displayName("Axis Bank 2")
                .build();

        assertThatThrownBy(() -> tenantService.onboardTenant(duplicateRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant code already exists");
    }

    @Test
    @DisplayName("Verify migration failure results in non-ACTIVE tenant status")
    void migrationFailureResultsInNonActiveStatus() {
        doThrow(new RuntimeException("Simulated Flyway Migration Failure"))
                .when(tenantMigrationService).migrateTenantSchema("tenant_failbank");

        CreateTenantRequest request = CreateTenantRequest.builder()
                .tenantCode("FAILBANK")
                .tenantName("Fail Bank")
                .displayName("Fail Bank")
                .build();

        assertThatThrownBy(() -> tenantService.onboardTenant(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Tenant onboarding migration failed");

        Tenant tenantInDb = tenantRepository.findByTenantCode("FAILBANK").orElseThrow();
        assertThat(tenantInDb.getStatus()).isNotEqualTo(TenantStatus.ACTIVE);
    }
}
