package com.tss.aml.services.implementation;

import com.tss.aml.dtos.tenant.NotificationResponse;
import com.tss.aml.entities.system.Users;
import com.tss.aml.entities.tenant.Notification;
import com.tss.aml.entities.tenant.SarStr;
import com.tss.aml.enums.NotificationChannel;
import com.tss.aml.enums.NotificationEventType;
import com.tss.aml.enums.UserRole;
import com.tss.aml.exceptions.base.ResourceNotFoundException;
import com.tss.aml.repositories.NotificationRepository;
import com.tss.aml.repositories.UserRepository;
import com.tss.aml.security.CustomUserDetails;
import com.tss.aml.services.EmailService;
import com.tss.aml.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Override
    @Transactional
    public void notifyBankAdminsOfSarStrFiling(SarStr sarStr, Users filingOfficer) {
        if (sarStr == null || filingOfficer == null || filingOfficer.getTenant() == null) {
            log.warn("Cannot dispatch SAR/STR notifications: sarStr, filingOfficer, or tenant is null");
            return;
        }

        UUID tenantId = filingOfficer.getTenant().getTenantId();
        List<Users> bankAdmins = userRepository.findByTenant_TenantIdAndRoleAndIsActiveTrue(tenantId, UserRole.BANK_ADMIN);

        if (bankAdmins.isEmpty()) {
            log.info("No active Bank Admins found for tenant ID '{}' to notify of SAR/STR filing '{}'", tenantId, sarStr.getReferenceNumber());
            return;
        }

        String officerName = (filingOfficer.getFirstName() + " " + filingOfficer.getLastName()).trim();
        String caseCode = sarStr.getAmlCase() != null ? sarStr.getAmlCase().getCaseCode() : "N/A";
        String reportTypeStr = sarStr.getReportType() != null ? sarStr.getReportType().name() : "SAR/STR";
        String message = String.format("A new %s (%s) has been filed by Compliance Officer %s for Case %s.",
                reportTypeStr, sarStr.getReferenceNumber(), officerName, caseCode);

        for (Users admin : bankAdmins) {
            // Idempotency check: Ensure single notification per Bank Admin per SAR/STR filing
            if (notificationRepository.existsByRecipient_UserIdAndSarStr_SarStrId(admin.getUserId(), sarStr.getSarStrId())) {
                log.info("Notification for SAR/STR '{}' already exists for Bank Admin '{}'. Skipping duplicate.",
                        sarStr.getReferenceNumber(), admin.getEmail());
                continue;
            }

            Notification notification = Notification.builder()
                    .recipient(admin)
                    .sarStr(sarStr)
                    .eventType(NotificationEventType.SAR_STR_FILED)
                    .channel(NotificationChannel.IN_APP)
                    .message(message)
                    .isRead(false)
                    .build();

            notificationRepository.save(notification);
            log.info("Saved in-app notification for Bank Admin '{}' for report '{}'", admin.getEmail(), sarStr.getReferenceNumber());

            // Exception-safe synchronous email notification
            try {
                emailService.sendSarStrFilingEmail(
                        admin.getEmail(),
                        admin.getFirstName(),
                        reportTypeStr,
                        sarStr.getReferenceNumber(),
                        caseCode,
                        officerName
                );
            } catch (Exception e) {
                log.error("Email delivery failed for Bank Admin '{}' on report '{}': {}", admin.getEmail(), sarStr.getReferenceNumber(), e.getMessage());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(CustomUserDetails currentUser, Pageable pageable) {
        return notificationRepository.findByRecipient_UserIdOrderByCreatedAtDesc(currentUser.getUserId(), pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUnreadUserNotifications(CustomUserDetails currentUser, Pageable pageable) {
        return notificationRepository.findByRecipient_UserIdAndIsReadFalseOrderByCreatedAtDesc(currentUser.getUserId(), pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(CustomUserDetails currentUser) {
        return notificationRepository.countByRecipient_UserIdAndIsReadFalse(currentUser.getUserId());
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(UUID notificationId, CustomUserDetails currentUser) {
        Notification notification = notificationRepository.findByNotificationIdAndRecipient_UserId(notificationId, currentUser.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification with ID " + notificationId + " not found or access denied"));

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification = notificationRepository.save(notification);
        }

        return mapToResponse(notification);
    }

    private NotificationResponse mapToResponse(Notification notification) {
        Users recipient = notification.getRecipient();
        String recipientName = recipient != null ? (recipient.getFirstName() + " " + recipient.getLastName()).trim() : null;

        return NotificationResponse.builder()
                .notificationId(notification.getNotificationId())
                .recipientId(recipient != null ? recipient.getUserId() : null)
                .recipientName(recipientName)
                .sarStrId(notification.getSarStr() != null ? notification.getSarStr().getSarStrId() : null)
                .eventType(notification.getEventType())
                .channel(notification.getChannel())
                .message(notification.getMessage())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
