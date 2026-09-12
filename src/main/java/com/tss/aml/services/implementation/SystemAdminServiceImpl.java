package com.tss.aml.services.implementation;

import com.tss.aml.dtos.rule.*;
import com.tss.aml.entities.system.BankRuleAssignment;
import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.system.RuleVersionHistory;
import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.entities.system.Tenant;
import com.tss.aml.enums.RuleStatus;
import com.tss.aml.enums.TenantStatus;
import com.tss.aml.exceptions.base.ResourceNotFoundException;
import com.tss.aml.repositories.BankRuleAssignmentRepository;
import com.tss.aml.repositories.RuleRepository;
import com.tss.aml.repositories.RuleVersionHistoryRepository;
import com.tss.aml.repositories.SystemAdminRepository;
import com.tss.aml.repositories.TenantRepository;
import com.tss.aml.entities.system.SystemAuditLog;
import com.tss.aml.repositories.SystemAuditLogRepository;
import com.tss.aml.ruleengine.validators.RuleParameterValidationService;
import com.tss.aml.security.CustomUserDetails;
import com.tss.aml.services.interfaces.ISystemAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SystemAdminServiceImpl implements ISystemAdminService {
    private final RuleRepository ruleRepository;
    private final RuleVersionHistoryRepository ruleVersionHistoryRepository;
    private final SystemAdminRepository systemAdminRepository;
    private final TenantRepository tenantRepository;
    private final BankRuleAssignmentRepository bankRuleAssignmentRepository;
    private final RuleParameterValidationService ruleParameterValidationService;
    private final SystemAuditLogRepository systemAuditLogRepository;

    @Override
    @Transactional
    public CreateRuleResponse addNewRule(CreateRuleRequest request) {
        ruleParameterValidationService.validate(request.getTypology(), request.getParameters());

        SystemAdmin currAdmin = getAuthenticatedSystemAdmin();

        String ruleCode = "RULE-" + request.getTypology().name() + "-"
                + UUID.randomUUID().toString().substring(0, 5).toUpperCase();

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

        if (currAdmin != null) {
            SystemAuditLog systemAudit = SystemAuditLog.builder()
                    .actor(currAdmin)
                    .action("RULE_CREATED")
                    .entityType("RULE")
                    .entityId(rule.getRuleId().toString())
                    .details("Created rule " + rule.getRuleCode() + " (" + rule.getRuleName() + ")")
                    .build();
            systemAuditLogRepository.save(systemAudit);
        }

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

    @Override
    @Transactional
    public RuleAssignmentResponse assignRuleToTenant(UUID ruleId, AssignRuleRequest request) {
        SystemAdmin admin = getAuthenticatedSystemAdmin();

        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + request.getTenantId()));

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new IllegalStateException("Tenant is not active: " + request.getTenantId());
        }

        Rule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + ruleId));

        if (rule.getStatus() != RuleStatus.ACTIVE) {
            throw new IllegalStateException("Rule is not active: " + ruleId);
        }

        if (bankRuleAssignmentRepository.existsByTenant_TenantIdAndRule_RuleId(tenant.getTenantId(),
                rule.getRuleId())) {
            throw new IllegalArgumentException("Rule is already assigned to tenant: " + tenant.getTenantId());
        }

        BankRuleAssignment assignment = BankRuleAssignment.builder()
                .tenant(tenant)
                .rule(rule)
                .assignedBy(admin)
                .assignedAt(LocalDateTime.now())
                .build();

        assignment = bankRuleAssignmentRepository.save(assignment);

        if (admin != null) {
            SystemAuditLog systemAudit = SystemAuditLog.builder()
                    .actor(admin)
                    .action("RULE_ASSIGNED")
                    .entityType("RULE_ASSIGNMENT")
                    .entityId(assignment.getAssignmentId().toString())
                    .details("Assigned rule " + rule.getRuleCode() + " to tenant " + tenant.getTenantCode() + " ("
                            + tenant.getTenantId() + ")")
                    .build();
            systemAuditLogRepository.save(systemAudit);
        }

        return mapToRuleAssignmentResponse(assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RuleAssignmentResponse> getAssignedRulesForTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        List<BankRuleAssignment> assignments = bankRuleAssignmentRepository.findByTenant_TenantId(tenant.getTenantId());
        return assignments.stream()
                .map(this::mapToRuleAssignmentResponse)
                .toList();
    }

    @Override
    @Transactional
    public void unassignRuleFromTenant(UUID ruleId, UUID tenantId) {
        BankRuleAssignment assignment = bankRuleAssignmentRepository
                .findByTenant_TenantIdAndRule_RuleId(tenantId, ruleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Assignment not found for tenant " + tenantId + " and rule " + ruleId));

        SystemAdmin admin = getAuthenticatedSystemAdmin();
        bankRuleAssignmentRepository.delete(assignment);

        if (admin != null) {
            SystemAuditLog systemAudit = SystemAuditLog.builder()
                    .actor(admin)
                    .action("RULE_UNASSIGNED")
                    .entityType("RULE_ASSIGNMENT")
                    .entityId(assignment.getAssignmentId().toString())
                    .details("Unassigned rule " + assignment.getRule().getRuleCode() + " from tenant "
                            + assignment.getTenant().getTenantCode())
                    .build();
            systemAuditLogRepository.save(systemAudit);
        }
    }

    @Override
    @Transactional
    public CreateRuleResponse updateRule(UUID ruleId, UpdateRuleRequest request) {
        Rule rule = ruleRepository.findById(ruleId).orElseThrow(
                () -> new ResourceNotFoundException("Rule having Id: " + ruleId + " not found."));

        ruleParameterValidationService.validate(rule.getTypology(), request.getParameters());

        SystemAdmin currAdmin = getAuthenticatedSystemAdmin();

        Map<String, Object> prevValues = rule.getParameters();

        rule.setRuleName(request.getRuleName());
        rule.setDescription(request.getDescription());
        rule.setDefaultSeverity(request.getDefaultSeverity());
        rule.setParameters(request.getParameters());

        rule = ruleRepository.save(rule);

        RuleVersionHistory versionHistory = RuleVersionHistory.builder()
                .rule(rule)
                .changedBy(currAdmin)
                .previousValues(prevValues != null ? prevValues.toString() : null)
                .updatedValues(rule.getParameters() != null ? rule.getParameters().toString() : null)
                .changedAt(LocalDateTime.now())
                .build();

        ruleVersionHistoryRepository.save(versionHistory);

        if (currAdmin != null) {
            SystemAuditLog systemAudit = SystemAuditLog.builder()
                    .actor(currAdmin)
                    .action("RULE_UPDATED")
                    .entityType("RULE")
                    .entityId(rule.getRuleId().toString())
                    .details("Updated rule " + rule.getRuleCode() + " (" + rule.getRuleName() + ")")
                    .build();
            systemAuditLogRepository.save(systemAudit);
        }

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

    @Override
    public Page<CreateRuleResponse> getAllRule(Pageable pageable) {
        Page<Rule> rulePage = ruleRepository.findAll(pageable);

        return rulePage.map(
                rule -> CreateRuleResponse.builder()
                        .ruleId(rule.getRuleId())
                        .ruleCode(rule.getRuleCode())
                        .ruleName(rule.getRuleName())
                        .description(rule.getDescription())
                        .typology(rule.getTypology())
                        .parameters(rule.getParameters())
                        .defaultSeverity(rule.getDefaultSeverity())
                        .status(rule.getStatus())
                        .isActive(rule.getIsDeleted() == null || !rule.getIsDeleted())
                        .build()

        );
    }

    private SystemAdmin getAuthenticatedSystemAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new IllegalStateException("Authentication context required");
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return systemAdminRepository.findById(userDetails.getUserId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Authenticated System Admin user not found: " + userDetails.getUserId()));
        } else if (principal instanceof SystemAdmin admin) {
            return admin;
        }

        throw new IllegalStateException("Principal is not a System Admin");
    }

    private RuleAssignmentResponse mapToRuleAssignmentResponse(BankRuleAssignment assignment) {
        return RuleAssignmentResponse.builder()
                .assignmentId(assignment.getAssignmentId())
                .tenantId(assignment.getTenant().getTenantId())
                .tenantCode(assignment.getTenant().getTenantCode())
                .tenantName(assignment.getTenant().getTenantName())
                .ruleId(assignment.getRule().getRuleId())
                .ruleCode(assignment.getRule().getRuleCode())
                .ruleName(assignment.getRule().getRuleName())
                .assignedByAdminId(
                        assignment.getAssignedBy() != null ? assignment.getAssignedBy().getSystemAdminId() : null)
                .assignedByEmail(assignment.getAssignedBy() != null ? assignment.getAssignedBy().getEmail() : null)
                .assignedAt(assignment.getAssignedAt())
                .build();
    }
}
