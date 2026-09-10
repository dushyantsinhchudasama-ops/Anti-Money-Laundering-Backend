package com.tss.aml.services;

import com.tss.aml.dtos.tenant.NotificationResponse;
import com.tss.aml.entities.system.Users;
import com.tss.aml.entities.tenant.SarStr;
import com.tss.aml.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationService {
    void notifyBankAdminsOfSarStrFiling(SarStr sarStr, Users filingOfficer);

    Page<NotificationResponse> getUserNotifications(CustomUserDetails currentUser, Pageable pageable);

    Page<NotificationResponse> getUnreadUserNotifications(CustomUserDetails currentUser, Pageable pageable);

    long getUnreadCount(CustomUserDetails currentUser);

    NotificationResponse markAsRead(UUID notificationId, CustomUserDetails currentUser);
}
