package com.tss.aml.repositories;

import com.tss.aml.entities.tenant.TransactionBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionBatchRepository extends JpaRepository<TransactionBatch, UUID> {
    Optional<TransactionBatch> findByBatchCode(String batchCode);

    Page<TransactionBatch> findByUploadedBy_Tenant_TenantId(UUID tenantId, Pageable pageable);
}

