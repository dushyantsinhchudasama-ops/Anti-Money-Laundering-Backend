package com.tss.aml.repositories;

import com.tss.aml.entities.tenant.AmlCase;
import com.tss.aml.enums.CaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AmlCaseRepository extends JpaRepository<AmlCase, UUID> {

    @Query("SELECT c FROM AmlCase c WHERE c.assignedTo.userId = :userId AND c.status NOT IN (:closedStatuses)")
    List<AmlCase> findOpenCasesByAssignedToUserId(
            @Param("userId") UUID userId,
            @Param("closedStatuses") List<CaseStatus> closedStatuses
    );

    @Query("SELECT COUNT(c) FROM AmlCase c WHERE c.assignedTo.userId = :userId AND c.status NOT IN (:closedStatuses)")
    long countActiveCasesByAssignedToUserId(
            @Param("userId") UUID userId,
            @Param("closedStatuses") List<CaseStatus> closedStatuses
    );

    List<AmlCase> findByAssignedTo_UserId(UUID userId);

    @Query("SELECT c FROM AmlCase c WHERE " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(:assignedToId IS NULL OR c.assignedTo.userId = :assignedToId) " +
           "ORDER BY c.createdAt DESC")
    Page<AmlCase> findCasesWithFilters(
            @Param("status") CaseStatus status,
            @Param("assignedToId") UUID assignedToId,
            Pageable pageable
    );

    long countByAssignedTo_UserId(UUID userId);

    long countByAssignedTo_UserIdAndStatus(UUID userId, CaseStatus status);

    @Query("SELECT DISTINCT c FROM AmlCase c JOIN c.alerts a WHERE a.transaction.originatorAccount.accountId = :accountId AND c.caseId <> :currentCaseId ORDER BY c.createdAt DESC")
    List<AmlCase> findHistoricalCasesByAccountId(@Param("accountId") UUID accountId, @Param("currentCaseId") UUID currentCaseId);
}

