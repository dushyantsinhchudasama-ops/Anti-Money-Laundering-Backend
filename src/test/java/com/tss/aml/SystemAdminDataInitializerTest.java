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
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        tenantRepository.deleteAll();
    }

    @Test
    @DisplayName("Verify default System Admin is created when none exists and password is BCrypt hashed")
    void defaultSystemAdminIsCreatedAndHashed() {
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
