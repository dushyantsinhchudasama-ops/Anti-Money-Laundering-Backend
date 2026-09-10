package com.tss.aml.services.implementation;

import com.tss.aml.dtos.audit.AuditLogResponseDto;
import com.tss.aml.dtos.audit.SystemAuditLogResponseDto;
import com.tss.aml.entities.system.SystemAuditLog;
import com.tss.aml.entities.tenant.AmlCase;
import com.tss.aml.entities.tenant.AuditLog;
import com.tss.aml.exceptions.base.ResourceNotFoundException;
import com.tss.aml.repositories.AmlCaseRepository;
import com.tss.aml.repositories.AuditLogRepository;
import com.tss.aml.repositories.SystemAuditLogRepository;
import com.tss.aml.security.CustomUserDetails;
import com.tss.aml.services.interfaces.AuditLogQueryService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogQueryServiceImpl implements AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;
    private final SystemAuditLogRepository systemAuditLogRepository;
    private final AmlCaseRepository amlCaseRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponseDto> getTenantAuditLogs(
            UUID actorId,
            String entityType,
            String action,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable,
            CustomUserDetails currentUser
    ) {
        if (currentUser == null || currentUser.getTenantId() == null) {
            throw new AccessDeniedException("Access denied: Tenant context required.");
        }

        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (actorId != null) {
                predicates.add(cb.equal(root.get("actor").get("userId"), actorId));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), endDate));
            }

            if (query != null) {
                query.orderBy(cb.desc(root.get("timestamp")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<AuditLog> auditPage = auditLogRepository.findAll(spec, pageable);
        return auditPage.map(this::mapToAuditLogResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SystemAuditLogResponseDto> getSystemAuditLogs(
            UUID actorId,
            String entityType,
            String action,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    ) {
        Specification<SystemAuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (actorId != null) {
                predicates.add(cb.equal(root.get("actor").get("systemAdminId"), actorId));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), endDate));
            }

            if (query != null) {
                query.orderBy(cb.desc(root.get("timestamp")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<SystemAuditLog> systemAuditPage = systemAuditLogRepository.findAll(spec, pageable);
        return systemAuditPage.map(this::mapToSystemAuditLogResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogResponseDto> getCaseAuditLogs(UUID caseId, CustomUserDetails currentUser) {
        AmlCase amlCase = amlCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case with ID " + caseId + " not found"));

        if (amlCase.getAssignedTo() == null || !amlCase.getAssignedTo().getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Access denied: Case is not assigned to you.");
        }

        List<AuditLog> logs = auditLogRepository.findByEntityTypeAndEntityId("CASE", caseId.toString());
        return logs.stream().map(this::mapToAuditLogResponseDto).collect(Collectors.toList());
    }

    private AuditLogResponseDto mapToAuditLogResponseDto(AuditLog log) {
        var actor = log.getActor();
        String actorName = actor != null ? (actor.getFirstName() + " " + actor.getLastName()).trim() : null;
        String actorEmail = actor != null ? actor.getEmail() : null;
        String actorRole = actor != null && actor.getRole() != null ? actor.getRole().name() : null;
        UUID actorId = actor != null ? actor.getUserId() : null;

        return AuditLogResponseDto.builder()
                .auditId(log.getAuditId())
                .actorId(actorId)
                .actorName(actorName)
                .actorEmail(actorEmail)
                .actorRole(actorRole)
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .timestamp(log.getTimestamp())
                .build();
    }

    private SystemAuditLogResponseDto mapToSystemAuditLogResponseDto(SystemAuditLog log) {
        var actor = log.getActor();
        String actorEmail = actor != null ? actor.getEmail() : null;
        UUID actorId = actor != null ? actor.getSystemAdminId() : null;

        return SystemAuditLogResponseDto.builder()
                .auditId(log.getAuditId())
                .actorId(actorId)
                .actorEmail(actorEmail)
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .timestamp(log.getTimestamp())
                .build();
    }
}
