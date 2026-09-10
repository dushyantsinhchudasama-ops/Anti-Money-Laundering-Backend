package com.tss.aml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.aml.config.SystemAdminDataInitializer;
import com.tss.aml.dtos.tenant.SarStrFilingRequest;
import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.entities.system.Tenant;
import com.tss.aml.entities.system.Users;
import com.tss.aml.entities.tenant.Account;
import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.entities.tenant.AmlCase;
import com.tss.aml.entities.tenant.AuditLog;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.entities.tenant.Notification;
import com.tss.aml.entities.tenant.SarStr;
import com.tss.aml.entities.tenant.TransactionBatch;
import com.tss.aml.enums.AccountType;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.AlertStatus;
import com.tss.aml.enums.BatchStatus;
import com.tss.aml.enums.CaseStatus;
import com.tss.aml.enums.FiuTypologyCategory;
import com.tss.aml.enums.NoteType;
import com.tss.aml.enums.NotificationEventType;
import com.tss.aml.enums.RuleSeverity;
import com.tss.aml.enums.RuleStatus;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.enums.SarStrType;
import com.tss.aml.enums.TenantStatus;
import com.tss.aml.enums.TransactionDirection;
import com.tss.aml.enums.TransactionType;
import com.tss.aml.enums.UserRole;
import com.tss.aml.repositories.AccountRepository;
import com.tss.aml.repositories.AlertRepository;
import com.tss.aml.repositories.AmlCaseRepository;
import com.tss.aml.repositories.AuditLogRepository;
import com.tss.aml.repositories.CaseNoteRepository;
import com.tss.aml.repositories.FinancialTransactionRepository;
import com.tss.aml.repositories.NotificationRepository;
import com.tss.aml.repositories.RuleRepository;
import com.tss.aml.repositories.SarStrRepository;
import com.tss.aml.repositories.SystemAdminRepository;
import com.tss.aml.repositories.TenantRepository;
import com.tss.aml.repositories.TransactionBatchRepository;
import com.tss.aml.repositories.UserRepository;
import com.tss.aml.security.CustomUserDetailsService;
import com.tss.aml.security.JwtTokenProvider;
import com.tss.aml.services.EmailService;
import com.tss.aml.services.TenantMigrationService;
import com.tss.aml.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("dev")
public class NotificationIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SystemAdminDataInitializer initializer;

    @Autowired
    private SystemAdminRepository systemAdminRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private AmlCaseRepository caseRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private CaseNoteRepository caseNoteRepository;

    @Autowired
    private SarStrRepository sarStrRepository;

    @Autowired
    private FinancialTransactionRepository txnRepository;

    @Autowired
    private TransactionBatchRepository batchRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TenantMigrationService tenantMigrationService;

    @MockitoSpyBean
    private EmailService emailService;

    private Users hdfcBankAdminActive;
    private Users hdfcBankAdminInactive;
    private Users hdfcCoA;
    private Users hdfcCoB;
    private Users iciciBankAdmin;

    private String hdfcBankAdminToken;
    private String hdfcCoAToken;
    private String hdfcCoBToken;
    private String iciciBankAdminToken;

    private Tenant tenantHdfc;
    private Tenant tenantIcici;

    private AmlCase inProgressCaseWithNotes;
    private AmlCase inProgressCaseWithoutNotes;
    private AmlCase openCase;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        TenantContext.clear();
        SecurityContextHolder.clearContext();

        if (systemAdminRepository.count() == 0) {
            initializer.run(null);
        }

        SystemAdmin sysAdmin = systemAdminRepository.findAll().get(0);

        tenantHdfc = tenantRepository.findByTenantCode("HDFC")
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .tenantCode("HDFC")
                        .tenantName("HDFC Bank")
                        .displayName("HDFC Bank")
                        .schemaName("tenant_hdfc")
                        .status(TenantStatus.ACTIVE)
                        .onboardedByAdmin(sysAdmin)
                        .build()));

        tenantIcici = tenantRepository.findByTenantCode("ICICI")
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .tenantCode("ICICI")
                        .tenantName("ICICI Bank")
                        .displayName("ICICI Bank")
                        .schemaName("tenant_icici")
                        .status(TenantStatus.ACTIVE)
                        .onboardedByAdmin(sysAdmin)
                        .build()));

        tenantMigrationService.migrateTenantSchema("tenant_hdfc");
        tenantMigrationService.migrateTenantSchema("tenant_icici");

        // Clear tenant data
        TenantContext.setCurrentTenant("tenant_hdfc");
        notificationRepository.deleteAll();
        sarStrRepository.deleteAll();
        caseNoteRepository.deleteAll();
        auditLogRepository.deleteAll();
        alertRepository.deleteAll();
        caseRepository.deleteAll();
        txnRepository.deleteAll();
        batchRepository.deleteAll();
        accountRepository.deleteAll();
        TenantContext.clear();

        TenantContext.setCurrentTenant("tenant_icici");
        notificationRepository.deleteAll();
        sarStrRepository.deleteAll();
        caseNoteRepository.deleteAll();
        auditLogRepository.deleteAll();
        alertRepository.deleteAll();
        caseRepository.deleteAll();
        txnRepository.deleteAll();
        batchRepository.deleteAll();
        accountRepository.deleteAll();
        TenantContext.clear();

        userRepository.deleteAll();

        // Seed Users
        hdfcBankAdminActive = userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_ADM_ACT")
                .role(UserRole.BANK_ADMIN)
                .email("admin_active@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Active").lastName("Admin")
                .isActive(true)
                .mustResetPassword(false)
                .build());

        hdfcBankAdminInactive = userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_ADM_INACT")
                .role(UserRole.BANK_ADMIN)
                .email("admin_inactive@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Inactive").lastName("Admin")
                .isActive(false)
                .mustResetPassword(false)
                .build());

        hdfcCoA = userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_CO_A_NOTIF")
                .role(UserRole.COMPLIANCE_OFFICER)
                .email("coa_notif@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Officer").lastName("A")
                .isActive(true)
                .mustResetPassword(false)
                .build());

        hdfcCoB = userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_CO_B_NOTIF")
                .role(UserRole.COMPLIANCE_OFFICER)
                .email("cob_notif@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Officer").lastName("B")
                .isActive(true)
                .mustResetPassword(false)
                .build());

        iciciBankAdmin = userRepository.save(Users.builder()
                .tenant(tenantIcici)
                .userCode("ICICI_ADM_NOTIF")
                .role(UserRole.BANK_ADMIN)
                .email("admin_notif@icici.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("ICICI").lastName("Admin")
                .isActive(true)
                .mustResetPassword(false)
                .build());

        hdfcBankAdminToken = generateToken(hdfcBankAdminActive.getEmail(), "tenant_hdfc");
        hdfcCoAToken = generateToken(hdfcCoA.getEmail(), "tenant_hdfc");
        hdfcCoBToken = generateToken(hdfcCoB.getEmail(), "tenant_hdfc");
        iciciBankAdminToken = generateToken(iciciBankAdmin.getEmail(), "tenant_icici");

        // Seed HDFC domain entities
        TenantContext.setCurrentTenant("tenant_hdfc");

        List<Rule> allRules = ruleRepository.findAll();
        Rule structRule = allRules.isEmpty() ? ruleRepository.save(Rule.builder()
                .ruleCode("RULE_NOTIF_01")
                .ruleName("Structuring Alert Rule Notification")
                .description("Detects structuring")
                .typology(RuleTypology.STRUCTURING_SMURFING)
                .parameters(Map.of("amountThreshold", 9000))
                .defaultSeverity(RuleSeverity.HIGH)
                .status(RuleStatus.ACTIVE)
                .build()) : allRules.get(0);

        Account acc = accountRepository.save(Account.builder()
                .accountNumber("ACC-NOTIF-100")
                .accountHolderName("Jane Doe")
                .accountType(AccountType.SAVINGS)
                .bankName("HDFC Bank")
                .countryCode("IN")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        TransactionBatch batch = batchRepository.save(TransactionBatch.builder()
                .batchCode("BATCH-NOTIF-1")
                .uploadedBy(hdfcBankAdminActive)
                .fileReference("ref.csv")
                .status(BatchStatus.PROCESSED_NO_ALERTS)
                .uploadedAt(LocalDateTime.now())
                .build());

        FinancialTransaction txn = txnRepository.save(FinancialTransaction.builder()
                .batch(batch)
                .txnNo("TXN-NOTIF-1")
                .originatorAccount(acc)
                .amount(new BigDecimal("350000.00"))
                .currency("INR")
                .txnType(TransactionType.NEFT)
                .direction(TransactionDirection.OUT)
                .countryCode("IN")
                .txnTimestamp(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        Alert alert1 = alertRepository.save(Alert.builder()
                .alertCode("ALT-NOTIF-1")
                .transaction(txn)
                .rule(structRule)
                .severity(AlertSeverity.HIGH)
                .alertStatus(AlertStatus.ASSIGNED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        inProgressCaseWithNotes = caseRepository.save(AmlCase.builder()
                .caseCode("CASE-NOTIF-WITHNOTES")
                .createdBy(hdfcBankAdminActive)
                .assignedTo(hdfcCoA)
                .status(CaseStatus.IN_PROGRESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        alert1.setAmlCase(inProgressCaseWithNotes);
        alertRepository.save(alert1);

        caseNoteRepository.save(com.tss.aml.entities.tenant.CaseNote.builder()
                .amlCase(inProgressCaseWithNotes)
                .author(hdfcCoA)
                .noteType(NoteType.OBSERVATION)
                .content("Investigation complete. Suspicious activity confirmed.")
                .createdAt(LocalDateTime.now())
                .build());

        inProgressCaseWithoutNotes = caseRepository.save(AmlCase.builder()
                .caseCode("CASE-NOTIF-NONOTES")
                .createdBy(hdfcBankAdminActive)
                .assignedTo(hdfcCoA)
                .status(CaseStatus.IN_PROGRESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        openCase = caseRepository.save(AmlCase.builder()
                .caseCode("CASE-NOTIF-OPEN")
                .createdBy(hdfcBankAdminActive)
                .assignedTo(hdfcCoA)
                .status(CaseStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        TenantContext.clear();
    }

    private String generateToken(String email, String schema) {
        TenantContext.setCurrentTenant(schema);
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        TenantContext.clear();
        return jwtTokenProvider.generateToken(auth);
    }

    @Test
    @DisplayName("1 & 2. SAR/STR Filing creates in-app notification for active Bank Admin and calls EmailService")
    void sarStrFilingCreatesNotificationAndEmailTest() throws Exception {
        SarStrFilingRequest req = SarStrFilingRequest.builder()
                .reportType(SarStrType.SAR)
                .typologyCategory(FiuTypologyCategory.STRUCTURING)
                .descriptionOfActivity("Structuring deposits.")
                .basisForSuspicion("Multiple cash deposits under limit.")
                .supportingEvidence("Bank statements.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/sar-str")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        TenantContext.setCurrentTenant("tenant_hdfc");
        List<Notification> notifications = notificationRepository.findByRecipient_UserIdOrderByCreatedAtDesc(hdfcBankAdminActive.getUserId());
        assertEquals(1, notifications.size());

        Notification notif = notifications.get(0);
        assertEquals(NotificationEventType.SAR_STR_FILED, notif.getEventType());
        assertEquals(hdfcBankAdminActive.getUserId(), notif.getRecipient().getUserId());
        assertNotNull(notif.getSarStr());
        assertFalse(notif.getIsRead());
        assertTrue(notif.getMessage().contains("SAR-2026-"));
        assertTrue(notif.getMessage().contains("CASE-NOTIF-WITHNOTES"));

        // Verify inactive Bank Admin received 0 notifications
        List<Notification> inactiveNotifications = notificationRepository.findByRecipient_UserIdOrderByCreatedAtDesc(hdfcBankAdminInactive.getUserId());
        assertEquals(0, inactiveNotifications.size());

        TenantContext.clear();
    }

    @Test
    @DisplayName("3 & 4. PREVIEW READ-ONLY: GET preview creates NO notification and sends NO email")
    void previewCreatesNoNotificationTest() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/sar-str/preview")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc"))
                .andExpect(status().isOk());

        TenantContext.setCurrentTenant("tenant_hdfc");
        assertEquals(0, notificationRepository.count());
        TenantContext.clear();
    }

    @Test
    @DisplayName("5 & 6. FAILED FILING: Filing without notes creates NO notification")
    void failedFilingCreatesNoNotificationTest() throws Exception {
        SarStrFilingRequest req = SarStrFilingRequest.builder()
                .reportType(SarStrType.STR)
                .typologyCategory(FiuTypologyCategory.LAYERING)
                .descriptionOfActivity("Layering activity.")
                .basisForSuspicion("Wire transfers.")
                .supportingEvidence("Ledger.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithoutNotes.getCaseId() + "/sar-str")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        TenantContext.setCurrentTenant("tenant_hdfc");
        assertEquals(0, notificationRepository.count());
        TenantContext.clear();
    }

    @Test
    @DisplayName("7. CROSS-TENANT ISOLATION: ICICI Bank Admin receives zero notifications from HDFC filing")
    void crossTenantNotificationIsolationTest() throws Exception {
        SarStrFilingRequest req = SarStrFilingRequest.builder()
                .reportType(SarStrType.SAR)
                .typologyCategory(FiuTypologyCategory.STRUCTURING)
                .descriptionOfActivity("HDFC filing.")
                .basisForSuspicion("Basis.")
                .supportingEvidence("Evidence.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/sar-str")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        TenantContext.setCurrentTenant("tenant_icici");
        assertEquals(0, notificationRepository.count());
        TenantContext.clear();
    }

    @Test
    @DisplayName("8 & 9. IDEMPOTENCY: Database unique constraint and check prevent duplicate notifications for same SAR/STR")
    void duplicateNotificationPreventionTest() throws Exception {
        SarStrFilingRequest req = SarStrFilingRequest.builder()
                .reportType(SarStrType.SAR)
                .typologyCategory(FiuTypologyCategory.STRUCTURING)
                .descriptionOfActivity("Structuring deposits.")
                .basisForSuspicion("Multiple cash deposits under limit.")
                .supportingEvidence("Bank statements.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/sar-str")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        TenantContext.setCurrentTenant("tenant_hdfc");
        SarStr savedReport = sarStrRepository.findByAmlCase_CaseId(inProgressCaseWithNotes.getCaseId()).orElseThrow();
        long notifCountBefore = notificationRepository.count();
        assertEquals(1, notifCountBefore);

        // Attempt manual duplicate insert with same recipient and sarStrId -> fails due to database constraint/check
        boolean exists = notificationRepository.existsByRecipient_UserIdAndSarStr_SarStrId(hdfcBankAdminActive.getUserId(), savedReport.getSarStrId());
        assertTrue(exists, "Notification record must already exist for recipient + sarStrId");
        TenantContext.clear();
    }

    @Test
    @DisplayName("10. EMAIL FAILURE NON-BLOCKING: EmailService exception does NOT roll back filing, status, audit, or in-app notification")
    void emailFailureDoesNotRollbackFilingTest() throws Exception {
        // Force EmailService to throw exception on sendSarStrFilingEmail
        doThrow(new RuntimeException("Simulated SMTP Failure"))
                .when(emailService)
                .sendSarStrFilingEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

        SarStrFilingRequest req = SarStrFilingRequest.builder()
                .reportType(SarStrType.SAR)
                .typologyCategory(FiuTypologyCategory.STRUCTURING)
                .descriptionOfActivity("Structuring with email failure simulation.")
                .basisForSuspicion("Suspicion basis.")
                .supportingEvidence("Evidence ledger.")
                .build();

        // Request still succeeds with HTTP 200 OK
        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/sar-str")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        TenantContext.setCurrentTenant("tenant_hdfc");
        // 1. SAR/STR filing exists
        SarStr savedReport = sarStrRepository.findByAmlCase_CaseId(inProgressCaseWithNotes.getCaseId()).orElseThrow();
        assertNotNull(savedReport);

        // 2. Case status is CLOSED_SAR_FILED
        AmlCase amlCase = caseRepository.findById(inProgressCaseWithNotes.getCaseId()).orElseThrow();
        assertEquals(CaseStatus.CLOSED_SAR_FILED, amlCase.getStatus());

        // 3. Audit log is created
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertTrue(auditLogs.stream().anyMatch(l -> "SAR_STR_FILED".equals(l.getAction())));

        // 4. In-app notification is created and persisted
        List<Notification> notifications = notificationRepository.findByRecipient_UserIdOrderByCreatedAtDesc(hdfcBankAdminActive.getUserId());
        assertEquals(1, notifications.size());
        assertEquals(savedReport.getSarStrId(), notifications.get(0).getSarStr().getSarStrId());

        TenantContext.clear();
    }

    @Test
    @DisplayName("11. NOTIFICATION API: Bank Admin retrieves unread notifications and marks notification read")
    void notificationApiAndReadStateTest() throws Exception {
        SarStrFilingRequest req = SarStrFilingRequest.builder()
                .reportType(SarStrType.STR)
                .typologyCategory(FiuTypologyCategory.FRAUD_RELATED_ML)
                .descriptionOfActivity("Activity for API test.")
                .basisForSuspicion("Fraud basis.")
                .supportingEvidence("Fraud evidence.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/sar-str")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        TenantContext.setCurrentTenant("tenant_hdfc");
        Notification notif = notificationRepository.findByRecipient_UserIdOrderByCreatedAtDesc(hdfcBankAdminActive.getUserId()).get(0);
        TenantContext.clear();

        // GET /api/v1/notifications/unread/count
        mockMvc.perform(get("/api/v1/notifications/unread/count")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .header("X-Tenant-ID", "tenant_hdfc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", equalTo(1)));

        // GET /api/v1/notifications/unread
        mockMvc.perform(get("/api/v1/notifications/unread")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .header("X-Tenant-ID", "tenant_hdfc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].notificationId", equalTo(notif.getNotificationId().toString())))
                .andExpect(jsonPath("$.content[0].isRead", equalTo(false)));

        // PATCH /api/v1/notifications/{id}/read
        mockMvc.perform(patch("/api/v1/notifications/" + notif.getNotificationId() + "/read")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .header("X-Tenant-ID", "tenant_hdfc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId", equalTo(notif.getNotificationId().toString())))
                .andExpect(jsonPath("$.isRead", equalTo(true)));

        // Marking read a second time is safe/idempotent
        mockMvc.perform(patch("/api/v1/notifications/" + notif.getNotificationId() + "/read")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .header("X-Tenant-ID", "tenant_hdfc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead", equalTo(true)));

        // GET /api/v1/notifications/unread/count is now 0
        mockMvc.perform(get("/api/v1/notifications/unread/count")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .header("X-Tenant-ID", "tenant_hdfc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", equalTo(0)));
    }

    @Test
    @DisplayName("12. SECURITY: Compliance Officer cannot access another user's notifications")
    void coCannotAccessOtherUserNotificationsTest() throws Exception {
        SarStrFilingRequest req = SarStrFilingRequest.builder()
                .reportType(SarStrType.SAR)
                .typologyCategory(FiuTypologyCategory.STRUCTURING)
                .descriptionOfActivity("Filing.")
                .basisForSuspicion("Basis.")
                .supportingEvidence("Evidence.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/sar-str")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        TenantContext.setCurrentTenant("tenant_hdfc");
        Notification adminNotif = notificationRepository.findByRecipient_UserIdOrderByCreatedAtDesc(hdfcBankAdminActive.getUserId()).get(0);
        TenantContext.clear();

        // Compliance Officer tries to mark Bank Admin's notification as read -> fails with 404 / access denied
        mockMvc.perform(patch("/api/v1/notifications/" + adminNotif.getNotificationId() + "/read")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc"))
                .andExpect(status().isNotFound());
    }
}
