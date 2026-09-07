package com.tss.aml.services.implementation;

import com.tss.aml.dtos.tenant.AlertResponse;
import com.tss.aml.dtos.tenant.CaseResponse;
import com.tss.aml.dtos.tenant.ComplianceOfficerWorkloadResponse;
import com.tss.aml.dtos.tenant.CreateCaseRequest;
import com.tss.aml.dtos.tenant.ReassignCaseRequest;
import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.entities.tenant.AmlCase;
import com.tss.aml.entities.tenant.AuditLog;
import com.tss.aml.entities.tenant.CaseNote;
import com.tss.aml.entities.tenant.Notification;
import com.tss.aml.entities.system.Users;
import com.tss.aml.enums.*;
import com.tss.aml.exceptions.base.ResourceNotFoundException;
import com.tss.aml.repositories.AlertRepository;
import com.tss.aml.repositories.AmlCaseRepository;
import com.tss.aml.repositories.AuditLogRepository;
import com.tss.aml.repositories.NotificationRepository;
import com.tss.aml.repositories.UserRepository;
import com.tss.aml.security.CustomUserDetails;
import com.tss.aml.services.interfaces.CaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Slf4j
public class CaseServiceImpl implements CaseService {

    private final AmlCaseRepository amlCaseRepository;
    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationRepository notificationRepository;

    private static final List<CaseStatus> CLOSED_STATUSES = List.of(
            CaseStatus.CLOSED_SAR_FILED,
            CaseStatus.CLOSED_NO_ACTION
    );

    @Override
    @Transactional(readOnly = true)
    public List<ComplianceOfficerWorkloadResponse> getComplianceOfficerWorkloads(CustomUserDetails currentUser) {
        Page<Users> officers = userRepository.findAllByTenant_TenantIdAndRole(
                currentUser.getTenantId(),
                UserRole.COMPLIANCE_OFFICER,
                Pageable.unpaged()
        );

        return officers.getContent().stream().map(user -> {
            long activeCases = amlCaseRepository.countActiveCasesByAssignedToUserId(user.getUserId(), CLOSED_STATUSES);
            return ComplianceOfficerWorkloadResponse.builder()
                    .userId(user.getUserId())
                    .userCode(user.getUserCode())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .email(user.getEmail())
                    .employeeId(user.getEmployeeId())
                    .isActive(user.getIsActive())
                    .activeCaseCount(activeCases)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CaseResponse assignAlertsToCase(CreateCaseRequest request, CustomUserDetails currentUser) {
        Users bankAdminUser = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in user not found: " + currentUser.getUserId()));

        Users targetCO = userRepository.findByUserIdAndTenant_TenantCode(request.getAssigneeId(), currentUser.getTenantCode())
                .orElseThrow(() -> new ResourceNotFoundException("Compliance Officer not found with ID: " + request.getAssigneeId()));

        if (!targetCO.getIsActive()) {
            throw new IllegalArgumentException("Cannot assign case to an inactive Compliance Officer: " + targetCO.getEmail());
        }

        List<Alert> alerts = alertRepository.findByAlertIdIn(request.getAlertIds());
        if (alerts.size() != request.getAlertIds().size()) {
            throw new IllegalArgumentException("One or more alert IDs provided were not found");
        }

        for (Alert alert : alerts) {
            if (alert.getAlertStatus() == AlertStatus.CLOSED) {
                throw new IllegalArgumentException("Cannot assign closed alert: " + alert.getAlertCode());
            }
        }

        String caseCode = "CASE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        AmlCase newCase = AmlCase.builder()
                .caseCode(caseCode)
                .createdBy(bankAdminUser)
                .assignedTo(targetCO)
                .status(CaseStatus.OPEN)
                .alerts(new ArrayList<>())
                .notes(new ArrayList<>())
                .build();

        if (request.getInitialNote() != null && !request.getInitialNote().isBlank()) {
            CaseNote initialNote = CaseNote.builder()
                    .amlCase(newCase)
                    .author(bankAdminUser)
                    .noteType(NoteType.OBSERVATION)
                    .content(request.getInitialNote())
                    .build();
            newCase.getNotes().add(initialNote);
        }

        newCase = amlCaseRepository.save(newCase);

        for (Alert alert : alerts) {
            alert.setAmlCase(newCase);
            alert.setAlertStatus(AlertStatus.ASSIGNED);
        }
        alertRepository.saveAll(alerts);
        newCase.setAlerts(alerts);

        // Notification for CO
        Notification notification = Notification.builder()
                .recipient(targetCO)
                .eventType(NotificationEventType.CASE_ASSIGNED)
                .channel(NotificationChannel.IN_APP)
                .message("New case assigned to you: " + newCase.getCaseCode() + " with " + alerts.size() + " alert(s).")
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        // Audit Log
        AuditLog auditLog = AuditLog.builder()
                .actor(bankAdminUser)
                .action("CASE_ASSIGNED")
                .entityType("CASE")
                .entityId(newCase.getCaseId().toString())
                .details("Assigned case " + newCase.getCaseCode() + " with " + alerts.size() + " alert(s) to Compliance Officer " + targetCO.getEmail())
                .build();
        auditLogRepository.save(auditLog);

        log.info("Assigned {} alerts to new Case '{}' (ID: {}) for CO '{}' by Bank Admin '{}'",
                alerts.size(), newCase.getCaseCode(), newCase.getCaseId(), targetCO.getEmail(), currentUser.getUsername());

        return mapToCaseResponse(newCase);
    }

    @Override
    @Transactional
    public CaseResponse reassignCase(UUID caseId, ReassignCaseRequest request, CustomUserDetails currentUser) {
        AmlCase amlCase = amlCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case with ID " + caseId + " not found"));

        if (CLOSED_STATUSES.contains(amlCase.getStatus())) {
            throw new IllegalStateException("Cannot reassign a closed case: " + amlCase.getCaseCode());
        }

        Users newCO = userRepository.findByUserIdAndTenant_TenantCode(request.getNewAssigneeId(), currentUser.getTenantCode())
                .orElseThrow(() -> new ResourceNotFoundException("Target Compliance Officer not found with ID: " + request.getNewAssigneeId()));

        if (!newCO.getIsActive()) {
            throw new IllegalArgumentException("Cannot reassign case to an inactive user: " + newCO.getEmail());
        }

        Users bankAdminUser = userRepository.findById(currentUser.getUserId()).orElse(null);
        Users previousAssignee = amlCase.getAssignedTo();
        amlCase.setAssignedTo(newCO);
        amlCaseRepository.save(amlCase);

        // Notification for new CO
        Notification notification = Notification.builder()
                .recipient(newCO)
                .eventType(NotificationEventType.CASE_ASSIGNED)
                .channel(NotificationChannel.IN_APP)
                .message("Case " + amlCase.getCaseCode() + " has been reassigned to you." + (request.getReason() != null ? " Reason: " + request.getReason() : ""))
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        // Audit log
        AuditLog auditLog = AuditLog.builder()
                .actor(bankAdminUser)
                .action("CASE_REASSIGNED")
                .entityType("CASE")
                .entityId(amlCase.getCaseId().toString())
                .details("Reassigned case " + amlCase.getCaseCode() + " from " +
                        (previousAssignee != null ? previousAssignee.getEmail() : "unassigned") +
                        " to " + newCO.getEmail() +
                        (request.getReason() != null ? ". Reason: " + request.getReason() : ""))
                .build();
        auditLogRepository.save(auditLog);

        log.info("Reassigned Case '{}' (ID: {}) to CO '{}' by Bank Admin '{}'",
                amlCase.getCaseCode(), amlCase.getCaseId(), newCO.getEmail(), currentUser.getUsername());

        return mapToCaseResponse(amlCase);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CaseResponse> getCases(CaseStatus status, UUID assignedToId, Pageable pageable, CustomUserDetails currentUser) {
        Page<AmlCase> casesPage = amlCaseRepository.findCasesWithFilters(status, assignedToId, pageable);
        return casesPage.map(this::mapToCaseResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CaseResponse getCaseDetail(UUID caseId, CustomUserDetails currentUser) {
        AmlCase amlCase = amlCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case with ID " + caseId + " not found"));
        return mapToCaseResponse(amlCase);
    }

    private CaseResponse mapToCaseResponse(AmlCase amlCase) {
        List<AlertResponse> alertResponses = amlCase.getAlerts() != null ?
                amlCase.getAlerts().stream().map(this::mapToAlertResponse).collect(Collectors.toList()) : List.of();

        return CaseResponse.builder()
                .caseId(amlCase.getCaseId())
                .caseCode(amlCase.getCaseCode())
                .status(amlCase.getStatus())
                .createdById(amlCase.getCreatedBy() != null ? amlCase.getCreatedBy().getUserId() : null)
                .createdByName(amlCase.getCreatedBy() != null ? amlCase.getCreatedBy().getFirstName() + " " + amlCase.getCreatedBy().getLastName() : null)
                .assignedToId(amlCase.getAssignedTo() != null ? amlCase.getAssignedTo().getUserId() : null)
                .assignedToName(amlCase.getAssignedTo() != null ? amlCase.getAssignedTo().getFirstName() + " " + amlCase.getAssignedTo().getLastName() : null)
                .alertCount(alertResponses.size())
                .alerts(alertResponses)
                .createdAt(amlCase.getCreatedAt())
                .closedAt(amlCase.getClosedAt())
                .build();
    }

    private AlertResponse mapToAlertResponse(Alert alert) {
        return AlertResponse.builder()
                .alertId(alert.getAlertId())
                .alertCode(alert.getAlertCode())
                .transactionId(alert.getTransaction() != null ? alert.getTransaction().getTransactionId() : null)
                .transactionTxnNo(alert.getTransaction() != null ? alert.getTransaction().getTxnNo() : null)
                .ruleId(alert.getRule() != null ? alert.getRule().getRuleId() : null)
                .ruleCode(alert.getRule() != null ? alert.getRule().getRuleCode() : null)
                .ruleName(alert.getRule() != null ? alert.getRule().getRuleName() : null)
                .severity(alert.getSeverity())
                .alertStatus(alert.getAlertStatus())
                .caseId(alert.getAmlCase() != null ? alert.getAmlCase().getCaseId() : null)
                .caseCode(alert.getAmlCase() != null ? alert.getAmlCase().getCaseCode() : null)
                .createdAt(alert.getCreatedAt())
                .build();
    }
}
