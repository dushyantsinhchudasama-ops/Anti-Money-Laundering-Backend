package com.tss.aml.services;

public interface EmailService {
    void sendBankAdminWelcomeEmail(
            String adminEmail,
            String adminFirstName,
            String tenantName,
            String tenantCode,
            String temporaryPassword
    );

    void sendComplianceOfficerWelcomeEmail(
            String officerEmail,
            String officerFirstName,
            String tenantName,
            String tenantCode,
            String userCode,
            String temporaryPassword
    );

    void sendPasswordResetEmail(
            String userEmail,
            String firstName,
            String temporaryPassword
    );

    void sendSarStrFilingEmail(
            String adminEmail,
            String adminFirstName,
            String reportType,
            String referenceNumber,
            String caseCode,
            String officerName
    );
}
