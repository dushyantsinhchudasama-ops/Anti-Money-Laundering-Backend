package com.tss.aml.services.interfaces;

import com.tss.aml.dtos.rule.AssignRuleRequest;
import com.tss.aml.dtos.rule.CreateRuleRequest;
import com.tss.aml.dtos.rule.CreateRuleResponse;
import com.tss.aml.dtos.rule.RuleAssignmentResponse;

import java.util.List;
import java.util.UUID;

public interface ISystemAdminService {
    CreateRuleResponse addNewRule(CreateRuleRequest request);

    RuleAssignmentResponse assignRuleToTenant(UUID ruleId, AssignRuleRequest request);

    List<RuleAssignmentResponse> getAssignedRulesForTenant(UUID tenantId);

    void unassignRuleFromTenant(UUID ruleId, UUID tenantId);
}
