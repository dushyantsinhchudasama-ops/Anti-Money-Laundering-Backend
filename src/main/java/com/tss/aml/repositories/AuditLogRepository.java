package com.tss.aml.repositories;

import com.tss.aml.entities.tenant.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByEntityTypeAndEntityId(String entityType, String entityId);
    Page<AuditLog> findByActor_UserId(UUID actorId, Pageable pageable);
}
