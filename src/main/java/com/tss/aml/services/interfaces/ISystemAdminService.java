package com.tss.aml.services.interfaces;

import com.tss.aml.dtos.rule.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ISystemAdminService {
    CreateRuleResponse addNewRule(CreateRuleRequest request);

    RuleAssignmentResponse assignRuleToTenant(UUID ruleId, AssignRuleRequest request);

    List<RuleAssignmentResponse> getAssignedRulesForTenant(UUID tenantId);

    void unassignRuleFromTenant(UUID ruleId, UUID tenantId);

    CreateRuleResponse updateRule(UUID ruleId, UpdateRuleRequest request);

    Page<CreateRuleResponse> getAllRule(Pageable pageable);
}
