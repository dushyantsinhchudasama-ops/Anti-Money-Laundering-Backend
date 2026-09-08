package com.tss.aml.repositories;

import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID>, JpaSpecificationExecutor<Alert> {

    long countBySeverity(AlertSeverity severity);

    long countByAlertStatus(AlertStatus alertStatus);

    List<Alert> findByAlertIdIn(List<UUID> alertIds);

    long countByAmlCase_AssignedTo_UserId(UUID userId);

    List<Alert> findByTransaction_OriginatorAccount_AccountId(UUID accountId);
}
