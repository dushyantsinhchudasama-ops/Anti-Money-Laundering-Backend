package com.tss.aml.services;

public interface EmailService {
    void sendBankAdminWelcomeEmail(
            String adminEmail,
            String adminFirstName,
            String tenantName,
            String tenantCode,
            String temporaryPassword
    );
}
