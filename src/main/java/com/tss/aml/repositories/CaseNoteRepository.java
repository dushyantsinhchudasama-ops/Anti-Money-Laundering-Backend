package com.tss.aml.repositories;

import com.tss.aml.entities.tenant.CaseNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CaseNoteRepository extends JpaRepository<CaseNote, UUID> {

    List<CaseNote> findByAmlCase_CaseIdOrderByCreatedAtAsc(UUID caseId);

    boolean existsByAmlCase_CaseId(UUID caseId);
}
