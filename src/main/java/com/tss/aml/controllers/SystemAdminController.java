package com.tss.aml.controllers;

import com.tss.aml.dtos.rule.CreateRuleRequest;
import com.tss.aml.dtos.rule.CreateRuleResponse;
import com.tss.aml.services.interfaces.ISystemAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/system/admin")
public class SystemAdminController {
    private final ISystemAdminService systemAdminService;

    @PostMapping("/rules")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CreateRuleResponse> addNewRule(@Valid @RequestBody CreateRuleRequest request){
        CreateRuleResponse response = systemAdminService.addNewRule(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
