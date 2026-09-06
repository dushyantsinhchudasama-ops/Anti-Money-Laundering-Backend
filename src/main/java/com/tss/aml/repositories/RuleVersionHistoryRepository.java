package com.tss.aml.repositories;

import com.tss.aml.entities.system.RuleVersionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RuleVersionHistoryRepository extends JpaRepository<RuleVersionHistory, UUID> {
}
