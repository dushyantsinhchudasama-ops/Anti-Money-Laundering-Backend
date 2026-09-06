package com.tss.aml.services.interfaces;

import com.tss.aml.dtos.tenant.ComplianceOfficerRequest;
import com.tss.aml.dtos.tenant.ComplianceOfficerResponse;
import com.tss.aml.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BankAdminService {
    ComplianceOfficerResponse createComplianceOfficer(ComplianceOfficerRequest request, CustomUserDetails currentUser);
    ComplianceOfficerResponse getComplianceOfficer(UUID officerId, CustomUserDetails currentUser);
    Page<ComplianceOfficerResponse> getAllComplianceOfficerOfTenant(UUID tenantId, Pageable pageable);
    ComplianceOfficerResponse deactivateComplianceOfficer(UUID officerId, CustomUserDetails currentUser);
    ComplianceOfficerResponse resetComplianceOfficerPassword(UUID officerId, CustomUserDetails currentUser);
}
