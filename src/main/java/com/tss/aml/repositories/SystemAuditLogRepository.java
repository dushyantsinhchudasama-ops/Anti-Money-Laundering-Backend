package com.tss.aml.repositories;

import com.tss.aml.entities.system.SystemAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SystemAuditLogRepository extends JpaRepository<SystemAuditLog, UUID>, JpaSpecificationExecutor<SystemAuditLog> {
    List<SystemAuditLog> findByAction(String action);
}
