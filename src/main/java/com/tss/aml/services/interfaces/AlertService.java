package com.tss.aml.services.interfaces;

import com.tss.aml.dtos.tenant.AlertDetailResponse;
import com.tss.aml.dtos.tenant.AlertResponse;
import com.tss.aml.dtos.tenant.AlertStatsResponse;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.AlertStatus;
import com.tss.aml.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AlertService {
    Page<AlertResponse> getAlerts(
            AlertSeverity severity,
            UUID ruleId,
            AlertStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable,
            CustomUserDetails currentUser
    );

    AlertStatsResponse getAlertStats(CustomUserDetails currentUser);

    AlertDetailResponse getAlertDetail(UUID alertId, CustomUserDetails currentUser);
}
