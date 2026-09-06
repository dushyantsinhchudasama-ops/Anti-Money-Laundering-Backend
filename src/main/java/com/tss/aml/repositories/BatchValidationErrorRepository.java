package com.tss.aml.repositories;

import com.tss.aml.entities.tenant.BatchValidationError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BatchValidationErrorRepository extends JpaRepository<BatchValidationError, Long> {
    List<BatchValidationError> findByBatchBatchId(UUID batchId);
}
