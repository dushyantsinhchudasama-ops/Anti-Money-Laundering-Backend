package com.tss.aml.services;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from:noreply@aml.com}")
    private String fromAddress;

    @Value("${app.frontend.login-url:http://localhost:4200/login}")
    private String loginUrl;

    @Override
    public void sendBankAdminWelcomeEmail(
            String adminEmail,
            String adminFirstName,
            String tenantName,
            String tenantCode,
            String temporaryPassword
    ) {
        try {
            Context context = new Context();
            context.setVariable("adminFirstName", adminFirstName != null ? adminFirstName : "Bank Admin");
            context.setVariable("tenantName", tenantName);
            context.setVariable("tenantCode", tenantCode);
            context.setVariable("adminEmail", adminEmail);
            context.setVariable("temporaryPassword", temporaryPassword);
            context.setVariable("loginUrl", loginUrl);

            String htmlContent = templateEngine.process("email/tenant-welcome", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(adminEmail);
            helper.setSubject("Welcome to AML Compliance System");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Successfully sent welcome email to Bank Admin '{}' for tenant '{}'", adminEmail, tenantCode);
        } catch (Exception e) {
            log.error("Failed to send welcome email to Bank Admin '{}' for tenant '{}': {}", adminEmail, tenantCode, e.getMessage());
            // Do not rethrow exception to prevent corrupting database state
        }
    }
}
