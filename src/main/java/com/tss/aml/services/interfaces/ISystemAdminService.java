package com.tss.aml.services.interfaces;

import com.tss.aml.dtos.rule.CreateRuleRequest;
import com.tss.aml.dtos.rule.CreateRuleResponse;

public interface ISystemAdminService {
    public CreateRuleResponse addNewRule(CreateRuleRequest request);
}
