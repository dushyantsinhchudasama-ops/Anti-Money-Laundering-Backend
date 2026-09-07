package com.tss.aml.repositories;

import com.tss.aml.entities.system.BankRuleAssignment;
import com.tss.aml.entities.system.Rule;
import com.tss.aml.enums.RuleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankRuleAssignmentRepository extends JpaRepository<BankRuleAssignment, UUID> {

    List<BankRuleAssignment> findByTenant_TenantId(UUID tenantId);

    boolean existsByTenant_TenantIdAndRule_RuleId(UUID tenantId, UUID ruleId);

    Optional<BankRuleAssignment> findByTenant_TenantIdAndRule_RuleId(UUID tenantId, UUID ruleId);

    void deleteByTenant_TenantIdAndRule_RuleId(UUID tenantId, UUID ruleId);

    @Query("SELECT bra.rule FROM BankRuleAssignment bra WHERE bra.tenant.tenantId = :tenantId AND bra.rule.status = :status")
    List<Rule> findAssignedRulesByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") RuleStatus status);
}
