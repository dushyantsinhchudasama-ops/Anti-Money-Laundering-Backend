package com.tss.aml.enums;

/**
 * Mirrors the notification matrix in SRS section 4.1.
 */
public enum NotificationEventType {
    CASE_ASSIGNED,
    CASE_ESCALATED,
    SAR_STR_FILED,
    BATCH_COMPLETED,
    BATCH_FAILED,
    ACCOUNT_LOCKED,
    BANK_ADMIN_ONBOARDED,
    COMPLIANCE_OFFICER_ONBOARDED
}
