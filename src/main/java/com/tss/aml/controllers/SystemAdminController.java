package com.tss.aml.controllers;

import com.tss.aml.dtos.rule.*;
import com.tss.aml.services.interfaces.ISystemAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.tss.aml.dtos.audit.SystemAuditLogResponseDto;
import com.tss.aml.services.interfaces.AuditLogQueryService;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/system/admin")
public class SystemAdminController {
    private final ISystemAdminService systemAdminService;
    private final AuditLogQueryService auditLogQueryService;

    @PostMapping("/rules")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CreateRuleResponse> addNewRule(@Valid @RequestBody CreateRuleRequest request) {
        CreateRuleResponse response = systemAdminService.addNewRule(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PatchMapping("/rules/update/{ruleId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CreateRuleResponse> updateRule(
            @PathVariable("ruleId") UUID ruleId,
            @RequestBody UpdateRuleRequest request
    ) {
        CreateRuleResponse response = systemAdminService.updateRule(ruleId, request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/rules/all")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Page<CreateRuleResponse>> getAllRule(
            Pageable pageable
    ) {
        Page<CreateRuleResponse> response = systemAdminService.getAllRule(pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/rules/{ruleId}/assign")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<RuleAssignmentResponse> assignRuleToTenant(
            @PathVariable("ruleId") UUID ruleId,
            @Valid @RequestBody AssignRuleRequest request
    ) {
        RuleAssignmentResponse response = systemAdminService.assignRuleToTenant(ruleId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping({"/rules/tenants/{tenantId}", "/tenants/{tenantId}/rules"})
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<List<RuleAssignmentResponse>> getAssignedRulesForTenant(
            @PathVariable("tenantId") UUID tenantId
    ) {
        List<RuleAssignmentResponse> response = systemAdminService.getAssignedRulesForTenant(tenantId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping({"/rules/{ruleId}/unassign/{tenantId}", "/rules/{ruleId}/assign/{tenantId}"})
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Void> unassignRuleFromTenant(
            @PathVariable("ruleId") UUID ruleId,
            @PathVariable("tenantId") UUID tenantId
    ) {
        systemAdminService.unassignRuleFromTenant(ruleId, tenantId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Page<SystemAuditLogResponseDto>> getSystemAuditLogs(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable) {
        Page<SystemAuditLogResponseDto> logs = auditLogQueryService.getSystemAuditLogs(actorId, entityType, action, startDate, endDate, pageable);
        return ResponseEntity.ok(logs);
    }
}
