package com.tss.aml.config;

import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.repositories.SystemAdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SystemAdminDataInitializer implements ApplicationRunner {

    private final SystemAdminRepository systemAdminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap-admin.email:admin@aml.com}")
    private String email;

    @Value("${app.bootstrap-admin.password:Admin@123}")
    private String rawPassword;

    @Value("${app.bootstrap-admin.code:SYSADMIN001}")
    private String systemAdminCode;

    @Value("${app.bootstrap-admin.first-name:System}")
    private String firstName;

    @Value("${app.bootstrap-admin.last-name:Admin}")
    private String lastName;

    @Value("${app.bootstrap-admin.phone-number:0000000000}")
    private String phoneNumber;

    @Override
    public void run(ApplicationArguments args) {
        if (systemAdminRepository.count() > 0) {
            log.info("System Admin initialization skipped: at least one System Admin already exists.");
            return;
        }

        log.info("No System Admin found in public schema. Creating default System Admin: {}", email);

        SystemAdmin admin = new SystemAdmin();
        admin.setSystemAdminCode(systemAdminCode);
        admin.setFirstName(firstName);
        admin.setLastName(lastName);
        admin.setPhoneNumber(phoneNumber);
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        admin.setIsActive(true);
        admin.setFailedLoginCount(0);

        systemAdminRepository.save(admin);
        log.info("Default System Admin '{}' created successfully.", systemAdminCode);
    }
}
