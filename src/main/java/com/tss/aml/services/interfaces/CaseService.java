package com.tss.aml.services.interfaces;

import com.tss.aml.dtos.tenant.CaseResponse;
import com.tss.aml.dtos.tenant.ComplianceOfficerWorkloadResponse;
import com.tss.aml.dtos.tenant.CreateCaseRequest;
import com.tss.aml.dtos.tenant.ReassignCaseRequest;
import com.tss.aml.enums.CaseStatus;
import com.tss.aml.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CaseService {

    List<ComplianceOfficerWorkloadResponse> getComplianceOfficerWorkloads(CustomUserDetails currentUser);

    CaseResponse assignAlertsToCase(CreateCaseRequest request, CustomUserDetails currentUser);

    CaseResponse reassignCase(UUID caseId, ReassignCaseRequest request, CustomUserDetails currentUser);

    Page<CaseResponse> getCases(CaseStatus status, UUID assignedToId, Pageable pageable, CustomUserDetails currentUser);

    CaseResponse getCaseDetail(UUID caseId, CustomUserDetails currentUser);
}
