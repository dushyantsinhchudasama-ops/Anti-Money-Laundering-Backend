package com.tss.aml.services.implementation;

import com.tss.aml.dtos.rule.CreateRuleRequest;
import com.tss.aml.dtos.rule.CreateRuleResponse;
import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.system.RuleVersionHistory;
import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.enums.RuleStatus;
import com.tss.aml.repositories.RuleRepository;
import com.tss.aml.repositories.RuleVersionHistoryRepository;
import com.tss.aml.repositories.SystemAdminRepository;
import com.tss.aml.security.CustomUserDetails;
import com.tss.aml.services.interfaces.ISystemAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SystemAdminServiceImpl implements ISystemAdminService {
    private final RuleRepository ruleRepository;
    private final RuleVersionHistoryRepository ruleVersionHistoryRepository;
    private final SystemAdminRepository systemAdminRepository;

    @Override
    @Transactional
    public CreateRuleResponse addNewRule(CreateRuleRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new IllegalStateException("Authentication context required to add new rule");
        }

        Object principal = auth.getPrincipal();
        SystemAdmin currAdmin = null;
        if (principal instanceof CustomUserDetails userDetails) {
            currAdmin = systemAdminRepository.findById(userDetails.getUserId()).orElse(null);
        } else if (principal instanceof SystemAdmin admin) {
            currAdmin = admin;
        }

        String ruleCode = "RULE-" + request.getTypology().name() + "-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();

        Rule rule = Rule.builder()
                .ruleCode(ruleCode)
                .ruleName(request.getRuleName())
                .description(request.getDescription())
                .typology(request.getTypology())
                .parameters(request.getParameters())
                .defaultSeverity(request.getDefaultSeverity())
                .status(RuleStatus.ACTIVE)
                .build();

        rule = ruleRepository.save(rule);

        RuleVersionHistory versionHistory = RuleVersionHistory.builder()
                .rule(rule)
                .changedBy(currAdmin)
                .previousValues(null)
                .updatedValues(rule.getParameters() != null ? rule.getParameters().toString() : null)
                .changedAt(LocalDateTime.now())
                .build();

        ruleVersionHistoryRepository.save(versionHistory);

        return CreateRuleResponse.builder()
                .ruleId(rule.getRuleId())
                .ruleCode(rule.getRuleCode())
                .ruleName(rule.getRuleName())
                .description(rule.getDescription())
                .typology(rule.getTypology())
                .parameters(rule.getParameters())
                .defaultSeverity(rule.getDefaultSeverity())
                .status(rule.getStatus())
                .isActive(rule.getIsDeleted() == null || !rule.getIsDeleted())
                .build();
    }
}
