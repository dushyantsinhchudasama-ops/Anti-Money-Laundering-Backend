package com.tss.aml.controllers;

import com.tss.aml.dtos.tenant.CreateTenantRequest;
import com.tss.aml.dtos.tenant.CreateTenantResponse;
import com.tss.aml.tenant.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import com.tss.aml.dtos.tenant.CreateBankAdminRequest;
import com.tss.aml.dtos.tenant.CreateBankAdminResponse;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    public ResponseEntity<CreateTenantResponse> onboardTenant(@Valid @RequestBody CreateTenantRequest request) {
        CreateTenantResponse response = tenantService.onboardTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<java.util.List<com.tss.aml.entities.system.Tenant>> getAllTenants() {
        return ResponseEntity.ok(tenantService.getAllTenants());
    }


    @PostMapping("/{tenantId}/users")
    public ResponseEntity<CreateBankAdminResponse> createBankAdmin(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateBankAdminRequest request
    ) {
        CreateBankAdminResponse response = tenantService.createBankAdmin(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

