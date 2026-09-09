package com.tss.aml.repositories;

import com.tss.aml.entities.tenant.SarStr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SarStrRepository extends JpaRepository<SarStr, UUID>, JpaSpecificationExecutor<SarStr> {
    Optional<SarStr> findByAmlCase_CaseId(UUID caseId);
    boolean existsByAmlCase_CaseId(UUID caseId);
    Optional<SarStr> findByReferenceNumber(String referenceNumber);
}
