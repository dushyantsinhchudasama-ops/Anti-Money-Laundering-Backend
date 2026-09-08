package com.tss.aml.services.interfaces;

import com.tss.aml.dtos.tenant.AlertDetailResponse;
import com.tss.aml.dtos.tenant.AlertResponse;
import com.tss.aml.dtos.tenant.CaseResponse;
import com.tss.aml.dtos.tenant.ComplianceOfficerDashboardResponse;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.AlertStatus;
import com.tss.aml.enums.CaseStatus;
import com.tss.aml.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.tss.aml.dtos.tenant.CaseInvestigationResponse;

import java.time.LocalDateTime;
import java.util.UUID;

import com.tss.aml.dtos.tenant.CaseNoteCreateRequest;
import com.tss.aml.dtos.tenant.CaseNoteResponse;
import java.util.List;

public interface ComplianceOfficerService {

    ComplianceOfficerDashboardResponse getDashboard(CustomUserDetails currentUser);

    Page<CaseResponse> getAssignedCases(CaseStatus status, Pageable pageable, CustomUserDetails currentUser);

    CaseResponse getCaseDetail(UUID caseId, CustomUserDetails currentUser);

    Page<AlertResponse> getAssignedAlerts(
            AlertSeverity severity,
            UUID ruleId,
            AlertStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable,
            CustomUserDetails currentUser);

    AlertDetailResponse getAlertDetail(UUID alertId, CustomUserDetails currentUser);

    CaseResponse startInvestigation(UUID caseId, CustomUserDetails currentUser);

    CaseNoteResponse createCaseNote(UUID caseId, CaseNoteCreateRequest request, CustomUserDetails currentUser);

    List<CaseNoteResponse> getCaseNotes(UUID caseId, CustomUserDetails currentUser);

    CaseInvestigationResponse getCaseInvestigationData(UUID caseId, Pageable pageable, CustomUserDetails currentUser);

    CaseResponse closeCaseNoAction(UUID caseId, com.tss.aml.dtos.tenant.CloseCaseNoActionRequest request, CustomUserDetails currentUser);
}
