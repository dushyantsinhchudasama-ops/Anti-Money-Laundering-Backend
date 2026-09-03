package com.tss.aml.tenant;

import com.tss.aml.dtos.tenant.CreateTenantRequest;
import com.tss.aml.dtos.tenant.CreateTenantResponse;
import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.entities.system.Tenant;
import com.tss.aml.enums.TenantStatus;
import com.tss.aml.repositories.SystemAdminRepository;
import com.tss.aml.repositories.TenantRepository;
import com.tss.aml.services.TenantMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import com.tss.aml.security.CustomUserDetails;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


import com.tss.aml.dtos.tenant.CreateBankAdminRequest;
import com.tss.aml.dtos.tenant.CreateBankAdminResponse;
import com.tss.aml.entities.system.Users;
import com.tss.aml.enums.UserRole;
import com.tss.aml.repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;
    private final SystemAdminRepository systemAdminRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantMigrationService tenantMigrationService;


    public CreateTenantResponse onboardTenant(CreateTenantRequest request) {
        if (tenantRepository.existsByTenantCode(request.getTenantCode())) {
            throw new IllegalArgumentException("Tenant code already exists: " + request.getTenantCode());
        }

        SystemAdmin admin = getCurrentAuthenticatedSystemAdmin();

        String schemaName = generateSchemaName(request.getTenantCode());

        Tenant tenant = Tenant.builder()
                .tenantCode(request.getTenantCode())
                .tenantName(request.getTenantName())
                .displayName(request.getDisplayName())
                .schemaName(schemaName)
                .status(TenantStatus.ONBOARDING)
                .onboardedByAdmin(admin)
                .build();

        tenant = tenantRepository.save(tenant);

        try {
            tenantMigrationService.migrateTenantSchema(schemaName);
            tenant.setStatus(TenantStatus.ACTIVE);
            tenant = tenantRepository.save(tenant);
            log.info("Tenant '{}' onboarded successfully with active schema '{}'", tenant.getTenantCode(), schemaName);
        } catch (Exception e) {
            tenant.setStatus(TenantStatus.OFFBOARDED);
            tenantRepository.save(tenant);
            log.error("Failed to execute Flyway migration for tenant schema '{}': {}", schemaName, e.getMessage());
            throw new RuntimeException("Tenant onboarding migration failed: " + e.getMessage(), e);
        }

        return CreateTenantResponse.builder()
                .tenantId(tenant.getTenantId())
                .tenantCode(tenant.getTenantCode())
                .tenantName(tenant.getTenantName())
                .displayName(tenant.getDisplayName())
                .schemaName(tenant.getSchemaName())
                .status(tenant.getStatus())
                .onboardedByAdminId(admin.getSystemAdminId())
                .createdAt(tenant.getCreatedAt())
                .build();
    }

    private SystemAdmin getCurrentAuthenticatedSystemAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new IllegalStateException("Authentication context required to onboard tenant");
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof SystemAdmin admin) {
            return admin;
        }

        if (principal instanceof CustomUserDetails userDetails) {
            if (userDetails.getUserId() != null) {
                Optional<SystemAdmin> adminById = systemAdminRepository.findById(userDetails.getUserId());
                if (adminById.isPresent()) {
                    return adminById.get();
                }
            }
            return systemAdminRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new IllegalStateException("System Admin not found for email: " + userDetails.getUsername()));
        }

        if (principal instanceof UserDetails userDetails) {
            return systemAdminRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new IllegalStateException("System Admin not found for username: " + userDetails.getUsername()));
        }

        if (principal instanceof String text) {
            return systemAdminRepository.findByEmail(text)
                    .or(() -> systemAdminRepository.findBySystemAdminCode(text))
                    .orElseThrow(() -> new IllegalStateException("System Admin not found for identifier: " + text));
        }

        throw new IllegalStateException("Unsupported principal type in SecurityContext: " + principal.getClass().getName());
    }


    private String generateSchemaName(String tenantCode) {
        return "tenant_" + tenantCode.toLowerCase().replaceAll("[^a-z0-9]", "_");
    }

    public List<Tenant> getAllTenants() {
        return tenantRepository.findAll();
    }

    public Tenant getTenant(UUID tenantId) {


        return tenantRepository.findById(tenantId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Tenant not found: " + tenantId
                        )
                );
    }

    public String getSchemaName(UUID tenantId) {

        Tenant tenant = getTenant(tenantId);

                if (tenant.getStatus() != TenantStatus.ACTIVE) {
                        throw new IllegalStateException(
                                         "Tenant is not active: " + tenantId
                        );
                }

        if (tenant.getSchemaName() == null ||
                tenant.getSchemaName().isBlank()) {

            throw new IllegalStateException(
                    "Tenant does not have a configured schema"
            );
        }

        return tenant.getSchemaName();
    }

    public CreateBankAdminResponse createBankAdmin(UUID tenantId, CreateBankAdminRequest request) {
        SystemAdmin admin = getCurrentAuthenticatedSystemAdmin();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new IllegalStateException("Tenant is not active: " + tenantId);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        if (userRepository.existsByUserCode(request.getUserCode())) {
            throw new IllegalArgumentException("User code already exists: " + request.getUserCode());
        }

        String temporaryPassword = generateTemporaryPassword();
        String encodedPassword = passwordEncoder.encode(temporaryPassword);

        Users user = Users.builder()
                .tenant(tenant)
                .userCode(request.getUserCode())
                .role(UserRole.BANK_ADMIN)
                .employeeId(request.getEmployeeId())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .passwordHash(encodedPassword)
                .isActive(true)
                .mustResetPassword(true)
                .failedLoginCount(0)
                .build();

        user = userRepository.save(user);

        log.info("Created Bank Admin user '{}' (ID: {}) for tenant '{}' (ID: {}) by SystemAdmin '{}'",
                user.getEmail(), user.getUserId(), tenant.getTenantCode(), tenant.getTenantId(), admin.getEmail());

        return CreateBankAdminResponse.builder()
                .userId(user.getUserId())
                .userCode(user.getUserCode())
                .tenantId(tenant.getTenantId())
                .employeeId(user.getEmployeeId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .mustResetPassword(user.getMustResetPassword())
                .temporaryPassword(temporaryPassword)
                .createdAt(user.getCreatedAt())
                .build();
    }

    private String generateTemporaryPassword() {
        return "TmpAdmin@" + UUID.randomUUID().toString().substring(0, 8);
    }
}


