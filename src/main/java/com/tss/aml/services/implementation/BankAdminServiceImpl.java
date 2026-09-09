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

import com.tss.aml.services.EmailService;

import com.tss.aml.entities.tenant.Account;
import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.entities.tenant.SarStr;
import com.tss.aml.repositories.SarStrRepository;
import com.tss.aml.dtos.tenant.SarStrResponse;

@RequiredArgsConstructor
@Service
@Slf4j
public class BankAdminServiceImpl implements BankAdminService {
    private final UserRepository userRepository;
    private final TenantService tenantService;
    private final PasswordEncoder passwordEncoder;
    private final AmlCaseRepository amlCaseRepository;
    private final EmailService emailService;
    private final SarStrRepository sarStrRepository;

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

        emailService.sendComplianceOfficerWelcomeEmail(
                complianceOfficer.getEmail(),
                complianceOfficer.getFirstName(),
                tenant.getTenantName(),
                tenant.getTenantCode(),
                complianceOfficer.getUserCode(),
                temporaryPassword
        );

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
    public ComplianceOfficerResponse activateComplianceOfficer(UUID officerId, CustomUserDetails currentUser) {
        Users complianceOfficer = userRepository
                .findByUserIdAndTenant_TenantCode(officerId, currentUser.getTenantCode()).orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Compliance Officer having Id: " + officerId + " not found."));

        complianceOfficer.setIsActive(true);
        complianceOfficer = userRepository.save(complianceOfficer);

        log.info("Reactivated Compliance Officer '{}' (ID: {}) by Bank Admin '{}'",
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

        emailService.sendPasswordResetEmail(
                complianceOfficer.getEmail(),
                complianceOfficer.getFirstName(),
                newTempPassword
        );

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

    private String generateTemporaryPassword() {
        return "TmpOff@" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SarStrResponse> getSarStrFilingLog(Pageable pageable, CustomUserDetails currentUser) {
        Page<SarStr> filings = sarStrRepository.findAll(pageable);
        return filings.map(this::mapToSarStrResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getSarStrPdfForAdmin(UUID sarStrId, CustomUserDetails currentUser) {
        SarStr sarStr = sarStrRepository.findById(sarStrId)
                .orElseThrow(() -> new ResourceNotFoundException("SAR/STR report not found with ID " + sarStrId));

        if (sarStr.getPdfContent() == null) {
            throw new ResourceNotFoundException("PDF content not found for SAR/STR report " + sarStr.getReferenceNumber());
        }

        return sarStr.getPdfContent();
    }

    private SarStrResponse mapToSarStrResponse(SarStr sarStr) {
        Account account = null;
        if (sarStr.getAmlCase() != null && sarStr.getAmlCase().getAlerts() != null) {
            for (Alert alert : sarStr.getAmlCase().getAlerts()) {
                if (alert.getTransaction() != null && alert.getTransaction().getOriginatorAccount() != null) {
                    account = alert.getTransaction().getOriginatorAccount();
                    break;
                }
            }
        }

        return SarStrResponse.builder()
                .sarStrId(sarStr.getSarStrId())
                .caseId(sarStr.getAmlCase() != null ? sarStr.getAmlCase().getCaseId() : null)
                .caseCode(sarStr.getAmlCase() != null ? sarStr.getAmlCase().getCaseCode() : null)
                .reportType(sarStr.getReportType())
                .typologyCategory(sarStr.getTypologyCategory())
                .descriptionOfActivity(sarStr.getDescriptionOfActivity())
                .basisForSuspicion(sarStr.getBasisForSuspicion())
                .supportingEvidence(sarStr.getSupportingEvidence())
                .referenceNumber(sarStr.getReferenceNumber())
                .pdfReference(sarStr.getPdfReference())
                .filedById(sarStr.getFiledBy() != null ? sarStr.getFiledBy().getUserId() : null)
                .filedByName(sarStr.getFiledBy() != null ? sarStr.getFiledBy().getFirstName() + " " + sarStr.getFiledBy().getLastName() : null)
                .filedByEmail(sarStr.getFiledBy() != null ? sarStr.getFiledBy().getEmail() : null)
                .submittedAt(sarStr.getSubmittedAt())
                .accountNumber(account != null ? account.getAccountNumber() : null)
                .accountHolderName(account != null ? account.getAccountHolderName() : null)
                .build();
    }
}
