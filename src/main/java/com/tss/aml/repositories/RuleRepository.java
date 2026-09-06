package com.tss.aml.repositories;

import com.tss.aml.entities.system.Rule;
import com.tss.aml.enums.RuleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RuleRepository extends JpaRepository<Rule, UUID> {
    List<Rule> findByStatus(RuleStatus status);

    @Query("SELECT bra.rule FROM BankRuleAssignment bra WHERE bra.tenant.tenantId = :tenantId AND bra.rule.status = com.tss.aml.enums.RuleStatus.ACTIVE")
    List<Rule> findActiveRulesByTenantId(@Param("tenantId") UUID tenantId);
}
