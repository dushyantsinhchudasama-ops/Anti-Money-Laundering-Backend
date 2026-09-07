package com.tss.aml.services.implementation;

import com.tss.aml.dtos.tenant.ComplianceOfficerRequest;
import com.tss.aml.dtos.tenant.ComplianceOfficerResponse;
import com.tss.aml.entities.system.Tenant;
import com.tss.aml.entities.system.Users;
import com.tss.aml.enums.TenantStatus;
import com.tss.aml.enums.UserRole;
import com.tss.aml.exceptions.base.ResourceNotFoundException;
import com.tss.aml.repositories.UserRepository;
import com.tss.aml.security.CustomUserDetails;
import com.tss.aml.services.interfaces.BankAdminService;
import com.tss.aml.tenant.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.tss.aml.entities.tenant.AmlCase;
import com.tss.aml.enums.CaseStatus;
import com.tss.aml.repositories.AmlCaseRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import com.tss.aml.tenant.TenantContext;

@RequiredArgsConstructor
@Service
@Slf4j
public class BankAdminServiceImpl implements BankAdminService {
    private final UserRepository userRepository;
    private final TenantService tenantService;
    private final PasswordEncoder passwordEncoder;
    private final AmlCaseRepository amlCaseRepository;

    @Override
    @Transactional
    public ComplianceOfficerResponse createComplianceOfficer(ComplianceOfficerRequest request,
            CustomUserDetails currentUser) {
        Tenant tenant = tenantService.getTenant(currentUser.getTenantId());

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new IllegalStateException("Tenant is not active: " + currentUser.getTenantId());
        }

        String normalizedEmail = com.tss.aml.util.NormalizationUtils.normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        if (userRepository.existsByUserCode(request.getUserCode())) {
            throw new IllegalArgumentException("User code already exists: " + request.getUserCode());
        }

        String temporaryPassword = generateTemporaryPassword();
        String encodedPassword = passwordEncoder.encode(temporaryPassword);

        Users complianceOfficer = Users.builder()
                .tenant(tenant)
                .userCode(request.getUserCode())
                .role(UserRole.COMPLIANCE_OFFICER)
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

        complianceOfficer = userRepository.save(complianceOfficer);

        log.info("Created Compliance Officer user '{}' (ID: {}) for tenant '{}' (ID: {}) by BankAdmin '{}'",
                complianceOfficer.getEmail(), complianceOfficer.getUserId(), tenant.getTenantCode(),
                tenant.getTenantId(), currentUser.getUsername());

        return ComplianceOfficerResponse.builder()
                .userId(complianceOfficer.getUserId())
                .userCode(complianceOfficer.getUserCode())
                .tenantId(tenant.getTenantId())
                .employeeId(complianceOfficer.getEmployeeId())
                .firstName(complianceOfficer.getFirstName())
                .lastName(complianceOfficer.getLastName())
                .email(complianceOfficer.getEmail())
                .phoneNumber(complianceOfficer.getPhoneNumber())
                .role(complianceOfficer.getRole())
                .isActive(complianceOfficer.getIsActive())
                .mustResetPassword(complianceOfficer.getMustResetPassword())
                .temporaryPassword(temporaryPassword)
                .createdAt(complianceOfficer.getCreatedAt())
                .build();
    }

    @Override
    public ComplianceOfficerResponse getComplianceOfficer(UUID officerId, CustomUserDetails currentUser) {
        Users complianceOfficer = userRepository
                .findByUserIdAndTenant_TenantCode(officerId, currentUser.getTenantCode()).orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Compliance Officer having Id: " + officerId + " not found."));

        return ComplianceOfficerResponse.builder()
                .userId(complianceOfficer.getUserId())
                .userCode(complianceOfficer.getUserCode())
                .tenantId(complianceOfficer.getTenant() != null ? complianceOfficer.getTenant().getTenantId()
                        : currentUser.getTenantId())
                .employeeId(complianceOfficer.getEmployeeId())
                .firstName(complianceOfficer.getFirstName())
                .lastName(complianceOfficer.getLastName())
                .email(complianceOfficer.getEmail())
                .phoneNumber(complianceOfficer.getPhoneNumber())
                .role(complianceOfficer.getRole())
                .isActive(complianceOfficer.getIsActive())
                .mustResetPassword(complianceOfficer.getMustResetPassword())
                .createdAt(complianceOfficer.getCreatedAt())
                .build();
    }

    @Override
    public Page<ComplianceOfficerResponse> getAllComplianceOfficerOfTenant(UUID tenantId, Pageable pageable) {
        Page<Users> usersPage = userRepository.findAllByTenant_TenantIdAndRole(tenantId, UserRole.COMPLIANCE_OFFICER,
                pageable);

        return usersPage.map(user -> ComplianceOfficerResponse.builder()
                .userId(user.getUserId())
                .userCode(user.getUserCode())
                .tenantId(user.getTenant() != null ? user.getTenant().getTenantId() : null)
                .employeeId(user.getEmployeeId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .mustResetPassword(user.getMustResetPassword())
                .createdAt(user.getCreatedAt())
                .build());
    }

    @Override
    @Transactional
    public ComplianceOfficerResponse deactivateComplianceOfficer(UUID officerId, CustomUserDetails currentUser) {
        Users complianceOfficer = userRepository
                .findByUserIdAndTenant_TenantCode(officerId, currentUser.getTenantCode()).orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Compliance Officer having Id: " + officerId + " not found."));

        complianceOfficer.setIsActive(false);
        complianceOfficer = userRepository.save(complianceOfficer);

        String tenantSchema = complianceOfficer.getTenant() != null ? complianceOfficer.getTenant().getSchemaName()
                : null;
        if (tenantSchema == null && currentUser != null && currentUser.getTenantId() != null) {
            tenantSchema = tenantService.getSchemaName(currentUser.getTenantId());
        }

        String previousTenantSchema = TenantContext.getCurrentTenant();
        try {
            if (tenantSchema != null && !tenantSchema.isBlank()) {
                TenantContext.setCurrentTenant(tenantSchema);
            }

            Users bankAdminUser = userRepository.findById(currentUser.getUserId()).orElse(null);
            if (bankAdminUser != null) {
                List<CaseStatus> closedStatuses = List.of(CaseStatus.CLOSED_SAR_FILED, CaseStatus.CLOSED_NO_ACTION);
                List<AmlCase> openCases = amlCaseRepository.findOpenCasesByAssignedToUserId(officerId, closedStatuses);
                if (!openCases.isEmpty()) {
                    for (AmlCase amlCase : openCases) {
                        amlCase.setAssignedTo(bankAdminUser);
                    }
                    amlCaseRepository.saveAll(openCases);
                    log.info("Reassigned {} open cases from deactivated CO '{}' to Bank Admin '{}'",
                            openCases.size(), complianceOfficer.getEmail(), bankAdminUser.getEmail());
                }
            }
        } finally {
            if (previousTenantSchema != null) {
                TenantContext.setCurrentTenant(previousTenantSchema);
            } else {
                TenantContext.clear();
            }
        }

        log.info("Deactivated Compliance Officer '{}' (ID: {}) by Bank Admin '{}'",
                complianceOfficer.getEmail(), officerId, currentUser.getUsername());

        return ComplianceOfficerResponse.builder()
                .userId(complianceOfficer.getUserId())
                .userCode(complianceOfficer.getUserCode())
                .tenantId(complianceOfficer.getTenant() != null ? complianceOfficer.getTenant().getTenantId() : null)
                .employeeId(complianceOfficer.getEmployeeId())
                .firstName(complianceOfficer.getFirstName())
                .lastName(complianceOfficer.getLastName())
                .email(complianceOfficer.getEmail())
                .phoneNumber(complianceOfficer.getPhoneNumber())
                .role(complianceOfficer.getRole())
                .isActive(complianceOfficer.getIsActive())
                .mustResetPassword(complianceOfficer.getMustResetPassword())
                .createdAt(complianceOfficer.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public ComplianceOfficerResponse resetComplianceOfficerPassword(UUID officerId, CustomUserDetails currentUser) {
        Users complianceOfficer = userRepository
                .findByUserIdAndTenant_TenantCode(officerId, currentUser.getTenantCode()).orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Compliance Officer having Id: " + officerId + " not found."));

        String newTempPassword = generateTemporaryPassword();
        complianceOfficer.setPasswordHash(passwordEncoder.encode(newTempPassword));
        complianceOfficer.setMustResetPassword(true);
        complianceOfficer.setFailedLoginCount(0);
        complianceOfficer = userRepository.save(complianceOfficer);

        log.info("Reset password for Compliance Officer '{}' (ID: {}) by Bank Admin '{}'",
                complianceOfficer.getEmail(), officerId, currentUser.getUsername());

        return ComplianceOfficerResponse.builder()
                .userId(complianceOfficer.getUserId())
                .userCode(complianceOfficer.getUserCode())
                .tenantId(complianceOfficer.getTenant() != null ? complianceOfficer.getTenant().getTenantId() : null)
                .employeeId(complianceOfficer.getEmployeeId())
                .firstName(complianceOfficer.getFirstName())
                .lastName(complianceOfficer.getLastName())
                .email(complianceOfficer.getEmail())
                .phoneNumber(complianceOfficer.getPhoneNumber())
                .role(complianceOfficer.getRole())
                .isActive(complianceOfficer.getIsActive())
                .mustResetPassword(complianceOfficer.getMustResetPassword())
                .temporaryPassword(newTempPassword)
                .createdAt(complianceOfficer.getCreatedAt())
                .build();
    }

    private String generateTemporaryPassword() {
        return "TmpOff@" + UUID.randomUUID().toString().substring(0, 8);
    }

}
