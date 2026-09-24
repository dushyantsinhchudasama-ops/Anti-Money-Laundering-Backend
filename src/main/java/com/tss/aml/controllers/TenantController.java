package com.tss.aml.controllers;

import com.tss.aml.dtos.tenant.CreateBankAdminRequest;
import com.tss.aml.dtos.tenant.CreateBankAdminResponse;
import com.tss.aml.dtos.tenant.CreateTenantRequest;
import com.tss.aml.dtos.tenant.CreateTenantResponse;
import com.tss.aml.tenant.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@CrossOrigin
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CreateTenantResponse> onboardTenant(@Valid @RequestBody CreateTenantRequest request) {
        CreateTenantResponse response = tenantService.onboardTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<java.util.List<CreateTenantResponse>> getAllTenants() {
        return ResponseEntity.ok(tenantService.getAllTenantResponses());
    }

    @GetMapping("/{tenantId}/users")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<List<CreateBankAdminResponse>> getBankAdminsForTenant(
            @PathVariable UUID tenantId) {
        return ResponseEntity.ok(tenantService.getBankAdminsForTenant(tenantId));
    }

    @PatchMapping("/{tenantId}/users/{userId}/activate")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CreateBankAdminResponse> activateBankAdmin(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId) {
        return ResponseEntity.ok(tenantService.activateBankAdmin(tenantId, userId));
    }

    @PatchMapping("/{tenantId}/users/{userId}/deactivate")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CreateBankAdminResponse> deactivateBankAdmin(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId) {
        return ResponseEntity.ok(tenantService.deactivateBankAdmin(tenantId, userId));
    }

    @PostMapping("/{tenantId}/users/{userId}/reset-password")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CreateBankAdminResponse> resetBankAdminPassword(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId) {
        return ResponseEntity.ok(tenantService.resetBankAdminPassword(tenantId, userId));
    }

    @PostMapping("/{tenantId}/users")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CreateBankAdminResponse> createBankAdmin(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateBankAdminRequest request
    ) {
        CreateBankAdminResponse response = tenantService.createBankAdmin(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

