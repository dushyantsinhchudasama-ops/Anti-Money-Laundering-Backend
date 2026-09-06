package com.tss.aml.controllers;

import com.tss.aml.dtos.tenant.AlertDetailResponse;
import com.tss.aml.dtos.tenant.AlertResponse;
import com.tss.aml.dtos.tenant.AlertStatsResponse;
import com.tss.aml.dtos.tenant.CaseResponse;
import com.tss.aml.dtos.tenant.ComplianceOfficerRequest;
import com.tss.aml.dtos.tenant.ComplianceOfficerResponse;
import com.tss.aml.dtos.tenant.ComplianceOfficerWorkloadResponse;
import com.tss.aml.dtos.tenant.CreateCaseRequest;
import com.tss.aml.dtos.tenant.ReassignCaseRequest;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.AlertStatus;
import com.tss.aml.enums.CaseStatus;
import com.tss.aml.security.CustomUserDetails;
import com.tss.aml.services.interfaces.AlertService;
import com.tss.aml.services.interfaces.BankAdminService;
import com.tss.aml.services.interfaces.CaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/bank/admin")
public class BankAdminController {
    private final BankAdminService bankAdminService;
    private final AlertService alertService;
    private final CaseService caseService;

    // --- User Management (Compliance Officers) ---

    @PostMapping("/add-compliance-officer")
    @PreAuthorize("hasRole('BANK_ADMIN')")
    public ResponseEntity<ComplianceOfficerResponse> addComplianceOfficer(
            @Valid @RequestBody ComplianceOfficerRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser){
        ComplianceOfficerResponse response = bankAdminService.createComplianceOfficer(request, currentUser);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/get-officer/{officerId}")
    @PreAuthorize("hasRole('BANK_ADMIN')")
    public ResponseEntity<ComplianceOfficerResponse> getComplianceOfficer(
            @PathVariable String officerId,
            @AuthenticationPrincipal CustomUserDetails currentUser){
        UUID officerUuid = parseUuid(officerId);
        ComplianceOfficerResponse response = bankAdminService.getComplianceOfficer(officerUuid, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/compliance-officers")
    @PreAuthorize("hasRole('BANK_ADMIN')")
    public ResponseEntity<Page<ComplianceOfficerResponse>> getAllComplianceOfficerOfTenant(
            Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails currentUser){
        validateTenantAccess(currentUser);
        Page<ComplianceOfficerResponse> response = bankAdminService.getAllComplianceOfficerOfTenant(currentUser.getTenantId(), pageable);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/compliance-officers/{officerId}/deactivate")
    @PreAuthorize("hasRole('BANK_ADMIN')")
    public ResponseEntity<ComplianceOfficerResponse> deactivateComplianceOfficer(
            @PathVariable String officerId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        UUID officerUuid = parseUuid(officerId);
        ComplianceOfficerResponse response = bankAdminService.deactivateComplianceOfficer(officerUuid, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/compliance-officers/{officerId}/reset-password")
    @PreAuthorize("hasRole('BANK_ADMIN')")
    public ResponseEntity<ComplianceOfficerResponse> resetComplianceOfficerPassword(
            @PathVariable String officerId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        UUID officerUuid = parseUuid(officerId);
        ComplianceOfficerResponse response = bankAdminService.resetComplianceOfficerPassword(officerUuid, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/compliance-officers/workload")
    @PreAuthorize("hasRole('BANK_ADMIN')")
    public ResponseEntity<List<ComplianceOfficerWorkloadResponse>> getComplianceOfficerWorkloads(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        validateTenantAccess(currentUser);
        List<ComplianceOfficerWorkloadResponse> workloads = caseService.getComplianceOfficerWorkloads(currentUser);
        return ResponseEntity.ok(workloads);
    }

    // --- Alert Dashboard ---

    @GetMapping("/alerts")
    @PreAuthorize("hasRole('BANK_ADMIN')")
    public ResponseEntity<Page<AlertResponse>> getAlerts(
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) UUID ruleId,
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        validateTenantAccess(currentUser);
        Page<AlertResponse> alerts = alertService.getAlerts(severity, ruleId, status, startDate, endDate, pageable, currentUser);
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/alerts/stats")
    @PreAuthorize("hasRole('BANK_ADMIN')")
    public ResponseEntity<AlertStatsResponse> getAlertStats(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        validateTenantAccess(currentUser);
        AlertStatsResponse stats = alertService.getAlertStats(currentUser);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/alerts/{alertId}")
    @PreAuthorize("hasRole('BANK_ADMIN')")
    public ResponseEntity<AlertDetailResponse> getAlertDetail(
            @PathVariable String alertId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        validateTenantAccess(currentUser);
        UUID alertUuid = parseUuid(alertId);
        AlertDetailResponse response = alertService.getAlertDetail(alertUuid, currentUser);
        return ResponseEntity.ok(response);
    }

    // --- Case Tracking & Assignment ---

    @PostMapping("/cases/assign")
    @PreAuthorize("hasRole('BANK_ADMIN')")
    public ResponseEntity<CaseResponse> assignAlertsToCase(
            @Valid @RequestBody CreateCaseRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        validateTenantAccess(currentUser);
        CaseResponse response = caseService.assignAlertsToCase(request, currentUser);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/cases/{caseId}/reassign")
    @PreAuthorize("hasRole('BANK_ADMIN')")
    public ResponseEntity<CaseResponse> reassignCase(
            @PathVariable String caseId,
            @Valid @RequestBody ReassignCaseRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        validateTenantAccess(currentUser);
        UUID caseUuid = parseUuid(caseId);
        CaseResponse response = caseService.reassignCase(caseUuid, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/cases")
    @PreAuthorize("hasRole('BANK_ADMIN')")
    public ResponseEntity<Page<CaseResponse>> getCases(
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(required = false) UUID assignedToId,
            Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        validateTenantAccess(currentUser);
        Page<CaseResponse> cases = caseService.getCases(status, assignedToId, pageable, currentUser);
        return ResponseEntity.ok(cases);
    }

    @GetMapping("/cases/{caseId}")
    @PreAuthorize("hasRole('BANK_ADMIN')")
    public ResponseEntity<CaseResponse> getCaseDetail(
            @PathVariable String caseId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        validateTenantAccess(currentUser);
        UUID caseUuid = parseUuid(caseId);
        CaseResponse response = caseService.getCaseDetail(caseUuid, currentUser);
        return ResponseEntity.ok(response);
    }

    // --- Private Helper Methods ---

    private UUID parseUuid(String uuidStr) {
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID format: " + uuidStr);
        }
    }

    private void validateTenantAccess(CustomUserDetails currentUser) {
        if (currentUser == null || currentUser.getTenantId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User does not belong to an active tenant");
        }
    }
}
