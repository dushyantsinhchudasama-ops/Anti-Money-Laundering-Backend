package com.tss.aml;

import com.tss.aml.config.SystemAdminDataInitializer;
import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.repositories.SystemAdminRepository;
import com.tss.aml.repositories.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SystemAdminDataInitializerTest {

    @Autowired
    private SystemAdminDataInitializer initializer;

    @Autowired
    private SystemAdminRepository systemAdminRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private com.tss.aml.repositories.UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.tss.aml.repositories.RuleVersionHistoryRepository ruleVersionHistoryRepository;

    @Autowired
    private com.tss.aml.repositories.RuleRepository ruleRepository;

    @Autowired
    private com.tss.aml.repositories.AmlCaseRepository amlCaseRepository;

    @Autowired
    private com.tss.aml.repositories.FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private com.tss.aml.repositories.TransactionBatchRepository transactionBatchRepository;

    @Autowired
    private com.tss.aml.repositories.AlertRepository alertRepository;

    @Autowired
    private com.tss.aml.repositories.AuditLogRepository auditLogRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        try {
            java.util.List<String> schemas = jdbcTemplate.queryForList(
                    "SELECT DISTINCT table_schema FROM information_schema.tables WHERE table_name IN ('transaction_batch', 'alerts', 'audit_log', 'aml_case')", String.class);
            for (String s : schemas) {
                if (!"information_schema".equalsIgnoreCase(s) && !"pg_catalog".equalsIgnoreCase(s)) {
                    java.util.List<String> existingTables = jdbcTemplate.queryForList(
                            "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_name IN ('case_note', 'escalation', 'sar_str', 'audit_log', 'notification', 'alerts', 'aml_case', 'batch_validation_error', 'financial_transaction', 'transaction_batch', 'account')",
                            String.class, s);
                    for (String t : existingTables) {
                        try {
                            jdbcTemplate.execute("TRUNCATE TABLE \"" + s + "\".\"" + t + "\" CASCADE");
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
        ruleVersionHistoryRepository.deleteAll();
        ruleRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    @DisplayName("Verify default System Admin is created when none exists and password is BCrypt hashed")
    void defaultSystemAdminIsCreatedAndHashed() {
        ruleVersionHistoryRepository.deleteAll();
        ruleRepository.deleteAll();
        jdbcTemplate.execute("TRUNCATE TABLE public.system_audit_log CASCADE");
        systemAdminRepository.deleteAll();
        assertThat(systemAdminRepository.count()).isEqualTo(0);


        initializer.run(null);

        assertThat(systemAdminRepository.count()).isEqualTo(1);

        Optional<SystemAdmin> adminOpt = systemAdminRepository.findByEmail("admin@aml.com");
        assertThat(adminOpt).isPresent();

        SystemAdmin admin = adminOpt.get();
        assertThat(admin.getSystemAdminCode()).isEqualTo("SYSADMIN001");
        assertThat(admin.getFirstName()).isEqualTo("System");
        assertThat(admin.getLastName()).isEqualTo("Admin");
        assertThat(admin.getIsActive()).isTrue();

        // Verify password is NOT stored as plaintext and IS validly hashed
        assertThat(admin.getPasswordHash()).isNotEqualTo("Admin@123");
        assertThat(passwordEncoder.matches("Admin@123", admin.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Verify initializer is idempotent: running multiple times does not create duplicates")
    void initializerIsIdempotent() {
        if (systemAdminRepository.count() == 0) {
            initializer.run(null);
        }

        long countBefore = systemAdminRepository.count();
        assertThat(countBefore).isGreaterThanOrEqualTo(1);

        // Re-run initializer
        initializer.run(null);

        long countAfter = systemAdminRepository.count();
        assertThat(countAfter).isEqualTo(countBefore);
    }
}
