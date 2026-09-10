package com.tss.aml.services.interfaces;

import com.tss.aml.dtos.audit.AuditLogResponseDto;
import com.tss.aml.dtos.audit.SystemAuditLogResponseDto;
import com.tss.aml.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditLogQueryService {

    Page<AuditLogResponseDto> getTenantAuditLogs(
            UUID actorId,
            String entityType,
            String action,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable,
            CustomUserDetails currentUser
    );

    Page<SystemAuditLogResponseDto> getSystemAuditLogs(
            UUID actorId,
            String entityType,
            String action,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    List<AuditLogResponseDto> getCaseAuditLogs(UUID caseId, CustomUserDetails currentUser);
}
