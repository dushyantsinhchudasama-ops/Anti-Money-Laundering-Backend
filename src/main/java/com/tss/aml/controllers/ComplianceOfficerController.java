package com.tss.aml.controllers;

import com.tss.aml.dtos.tenant.AlertDetailResponse;
import com.tss.aml.dtos.tenant.AlertResponse;
import com.tss.aml.dtos.tenant.CaseResponse;
import com.tss.aml.dtos.tenant.ComplianceOfficerDashboardResponse;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.AlertStatus;
import com.tss.aml.enums.CaseStatus;
import com.tss.aml.security.CustomUserDetails;
import com.tss.aml.services.interfaces.ComplianceOfficerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.tss.aml.dtos.tenant.CaseNoteCreateRequest;
import com.tss.aml.dtos.tenant.CaseNoteResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import java.time.LocalDateTime;
import java.util.UUID;

@CrossOrigin
@RestController
@RequestMapping("/api/v1/compliance")
@PreAuthorize("hasRole('COMPLIANCE_OFFICER')")
@RequiredArgsConstructor
public class ComplianceOfficerController {

    private final ComplianceOfficerService complianceOfficerService;

    @GetMapping("/dashboard")
    public ResponseEntity<ComplianceOfficerDashboardResponse> getDashboard(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        ComplianceOfficerDashboardResponse dashboard = complianceOfficerService.getDashboard(currentUser);
        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/cases")
    public ResponseEntity<Page<CaseResponse>> getAssignedCases(
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        Pageable pageable = PageRequest.of(page, size);
        Page<CaseResponse> casesPage = complianceOfficerService.getAssignedCases(status, pageable, currentUser);
        return ResponseEntity.ok(casesPage);
    }

    @GetMapping("/cases/{caseId}")
    public ResponseEntity<CaseResponse> getCaseDetail(
            @PathVariable String caseId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        UUID caseUuid = parseUuid(caseId);
        CaseResponse response = complianceOfficerService.getCaseDetail(caseUuid, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cases/{caseId}/start-investigation")
    public ResponseEntity<CaseResponse> startInvestigation(
            @PathVariable String caseId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        UUID caseUuid = parseUuid(caseId);
        CaseResponse response = complianceOfficerService.startInvestigation(caseUuid, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/alerts")
    public ResponseEntity<Page<AlertResponse>> getAssignedAlerts(
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) UUID ruleId,
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AlertResponse> alertsPage = complianceOfficerService.getAssignedAlerts(
                severity, ruleId, status, startDate, endDate, pageable, currentUser);
        return ResponseEntity.ok(alertsPage);
    }

    @GetMapping("/alerts/{alertId}")
    public ResponseEntity<AlertDetailResponse> getAlertDetail(
            @PathVariable String alertId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        UUID alertUuid = parseUuid(alertId);
        AlertDetailResponse response = complianceOfficerService.getAlertDetail(alertUuid, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cases/{caseId}/notes")
    public ResponseEntity<CaseNoteResponse> createCaseNote(
            @PathVariable String caseId,
            @Valid @RequestBody CaseNoteCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        UUID caseUuid = parseUuid(caseId);
        CaseNoteResponse response = complianceOfficerService.createCaseNote(caseUuid, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/cases/{caseId}/notes")
    public ResponseEntity<List<CaseNoteResponse>> getCaseNotes(
            @PathVariable String caseId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        UUID caseUuid = parseUuid(caseId);
        List<CaseNoteResponse> response = complianceOfficerService.getCaseNotes(caseUuid, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/cases/{caseId}/investigation")
    public ResponseEntity<com.tss.aml.dtos.tenant.CaseInvestigationResponse> getCaseInvestigationData(
            @PathVariable String caseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        UUID caseUuid = parseUuid(caseId);
        Pageable pageable = PageRequest.of(page, size);
        com.tss.aml.dtos.tenant.CaseInvestigationResponse response = complianceOfficerService
                .getCaseInvestigationData(caseUuid, pageable, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cases/{caseId}/close-no-action")
    public ResponseEntity<CaseResponse> closeCaseNoAction(
            @PathVariable String caseId,
            @Valid @RequestBody com.tss.aml.dtos.tenant.CloseCaseNoActionRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        UUID caseUuid = parseUuid(caseId);
        CaseResponse response = complianceOfficerService.closeCaseNoAction(caseUuid, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/cases/{caseId}/sar-str/preview")
    public ResponseEntity<com.tss.aml.dtos.tenant.SarStrPreviewResponse> getSarStrPreview(
            @PathVariable String caseId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        UUID caseUuid = parseUuid(caseId);
        com.tss.aml.dtos.tenant.SarStrPreviewResponse response = complianceOfficerService.getSarStrPreview(caseUuid, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cases/{caseId}/sar-str")
    public ResponseEntity<com.tss.aml.dtos.tenant.SarStrResponse> fileSarStr(
            @PathVariable String caseId,
            @Valid @RequestBody com.tss.aml.dtos.tenant.SarStrFilingRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        UUID caseUuid = parseUuid(caseId);
        com.tss.aml.dtos.tenant.SarStrResponse response = complianceOfficerService.fileSarStr(caseUuid, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/cases/{caseId}/sar-str/pdf", produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getSarStrPdf(
            @PathVariable String caseId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        UUID caseUuid = parseUuid(caseId);
        byte[] pdfBytes = complianceOfficerService.getSarStrPdf(caseUuid, currentUser);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"SAR-STR-" + caseId + ".pdf\"")
                .body(pdfBytes);
    }

    private UUID parseUuid(String uuidStr) {
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID format: " + uuidStr);
        }
    }
}
