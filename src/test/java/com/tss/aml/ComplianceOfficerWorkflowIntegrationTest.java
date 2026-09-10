package com.tss.aml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.aml.config.SystemAdminDataInitializer;
import com.tss.aml.dtos.tenant.ComplianceOfficerRequest;
import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.entities.system.Tenant;
import com.tss.aml.entities.system.Users;
import com.tss.aml.entities.tenant.Account;
import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.entities.tenant.AmlCase;
import com.tss.aml.entities.tenant.AuditLog;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.entities.tenant.TransactionBatch;
import com.tss.aml.enums.AccountType;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.AlertStatus;
import com.tss.aml.enums.BatchStatus;
import com.tss.aml.enums.CaseStatus;
import com.tss.aml.enums.RuleSeverity;
import com.tss.aml.enums.RuleStatus;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.enums.TenantStatus;
import com.tss.aml.enums.TransactionDirection;
import com.tss.aml.enums.TransactionType;
import com.tss.aml.enums.UserRole;
import com.tss.aml.repositories.AccountRepository;
import com.tss.aml.repositories.AlertRepository;
import com.tss.aml.repositories.AmlCaseRepository;
import com.tss.aml.repositories.AuditLogRepository;
import com.tss.aml.repositories.FinancialTransactionRepository;
import com.tss.aml.repositories.RuleRepository;
import com.tss.aml.repositories.SystemAdminRepository;
import com.tss.aml.repositories.TenantRepository;
import com.tss.aml.repositories.TransactionBatchRepository;
import com.tss.aml.repositories.UserRepository;
import com.tss.aml.security.CustomUserDetailsService;
import com.tss.aml.security.JwtTokenProvider;
import com.tss.aml.services.EmailService;
import com.tss.aml.services.TenantMigrationService;
import com.tss.aml.tenant.TenantContext;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ComplianceOfficerWorkflowIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SystemAdminDataInitializer initializer;

    @Autowired
    private SystemAdminRepository systemAdminRepository;

    @Autowired
    private TenantMigrationService tenantMigrationService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionBatchRepository batchRepository;

    @Autowired
    private FinancialTransactionRepository txnRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AmlCaseRepository caseRepository;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoSpyBean
    private EmailService emailService;

    private Tenant tenantHdfc;
    private Tenant tenantIcici;

    private Users hdfcBankAdmin;
    private Users hdfcCoA;
    private Users hdfcCoB;
    private Users iciciCo;

    private String hdfcBankAdminToken;
    private String hdfcCoAToken;
    private String hdfcCoBToken;
    private String iciciCoToken;

    private AmlCase caseCoA;
    private AmlCase caseCoB;

    private Alert alertCoA;
    private Alert alertCoB;
    private Alert alertUnassigned;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        TenantContext.clear();
        SecurityContextHolder.clearContext();

        when(mailSender.createMimeMessage()).thenAnswer(invocation ->
                new MimeMessage(Session.getInstance(new Properties()))
        );

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

        // Clear tenant tables to avoid foreign key violations on public.users
        TenantContext.setCurrentTenant("tenant_hdfc");
        auditLogRepository.deleteAll();
        alertRepository.deleteAll();
        caseRepository.deleteAll();
        txnRepository.deleteAll();
        batchRepository.deleteAll();
        accountRepository.deleteAll();
        TenantContext.clear();

        TenantContext.setCurrentTenant("tenant_icici");
        auditLogRepository.deleteAll();
        alertRepository.deleteAll();
        caseRepository.deleteAll();
        txnRepository.deleteAll();
        batchRepository.deleteAll();
        accountRepository.deleteAll();
        TenantContext.clear();

        // Create Users in public.users
        jdbcTemplate.execute("TRUNCATE TABLE public.users CASCADE");

        hdfcBankAdmin = userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_ADMIN")
                .role(UserRole.BANK_ADMIN)
                .firstName("HDFC")
                .lastName("Admin")
                .email("admin@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .isActive(true)
                .mustResetPassword(false)
                .build());

        hdfcCoA = userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_CO_A")
                .role(UserRole.COMPLIANCE_OFFICER)
                .firstName("CO")
                .lastName("Alpha")
                .email("coa@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .isActive(true)
                .mustResetPassword(false)
                .build());

        hdfcCoB = userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_CO_B")
                .role(UserRole.COMPLIANCE_OFFICER)
                .firstName("CO")
                .lastName("Beta")
                .email("cob@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .isActive(true)
                .mustResetPassword(false)
                .build());

        iciciCo = userRepository.save(Users.builder()
                .tenant(tenantIcici)
                .userCode("ICICI_CO")
                .role(UserRole.COMPLIANCE_OFFICER)
                .firstName("ICICI")
                .lastName("CO")
                .email("co@icici.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .isActive(true)
                .mustResetPassword(false)
                .build());

        hdfcBankAdminToken = generateUserToken(hdfcBankAdmin);
        hdfcCoAToken = generateUserToken(hdfcCoA);
        hdfcCoBToken = generateUserToken(hdfcCoB);
        iciciCoToken = generateUserToken(iciciCo);

        // Setup tenant_hdfc domain entities
        TenantContext.setCurrentTenant("tenant_hdfc");

        Rule testRule = ruleRepository.findAll().stream().findFirst()
                .orElseGet(() -> ruleRepository.save(Rule.builder()
                        .ruleCode("CTR-001")
                        .ruleName("High Value Transaction")
                        .description("Transaction exceeding threshold")
                        .typology(RuleTypology.LAYERING)
                        .parameters(Map.of())
                        .defaultSeverity(RuleSeverity.HIGH)
                        .status(RuleStatus.ACTIVE)
                        .build()));

        Account account = accountRepository.save(Account.builder()
                .accountNumber("ACC-1001")
                .accountHolderName("John Doe")
                .accountType(AccountType.SAVINGS)
                .countryCode("IN")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        TransactionBatch batch = batchRepository.save(TransactionBatch.builder()
                .batchCode("BATCH-001")
                .uploadedBy(hdfcBankAdmin)
                .fileReference("batch1.csv")
                .status(BatchStatus.PROCESSED_NO_ALERTS)
                .uploadedAt(LocalDateTime.now())
                .build());

        FinancialTransaction txn1 = txnRepository.save(FinancialTransaction.builder()
                .batch(batch)
                .txnNo("TXN-001")
                .originatorAccount(account)
                .amount(new BigDecimal("150000.00"))
                .currency("INR")
                .txnType(TransactionType.NEFT)
                .direction(TransactionDirection.OUT)
                .countryCode("IN")
                .txnTimestamp(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        FinancialTransaction txn2 = txnRepository.save(FinancialTransaction.builder()
                .batch(batch)
                .txnNo("TXN-002")
                .originatorAccount(account)
                .amount(new BigDecimal("250000.00"))
                .currency("INR")
                .txnType(TransactionType.NEFT)
                .direction(TransactionDirection.OUT)
                .countryCode("IN")
                .txnTimestamp(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        FinancialTransaction txn3 = txnRepository.save(FinancialTransaction.builder()
                .batch(batch)
                .txnNo("TXN-003")
                .originatorAccount(account)
                .amount(new BigDecimal("350000.00"))
                .currency("INR")
                .txnType(TransactionType.NEFT)
                .direction(TransactionDirection.OUT)
                .countryCode("IN")
                .txnTimestamp(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        caseCoA = caseRepository.save(AmlCase.builder()
                .caseCode("CASE-CO-A")
                .createdBy(hdfcBankAdmin)
                .assignedTo(hdfcCoA)
                .status(CaseStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        caseCoB = caseRepository.save(AmlCase.builder()
                .caseCode("CASE-CO-B")
                .createdBy(hdfcBankAdmin)
                .assignedTo(hdfcCoB)
                .status(CaseStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        alertCoA = alertRepository.save(Alert.builder()
                .alertCode("ALT-CO-A")
                .transaction(txn1)
                .rule(testRule)
                .severity(AlertSeverity.HIGH)
                .alertStatus(AlertStatus.ASSIGNED)
                .amlCase(caseCoA)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        alertCoB = alertRepository.save(Alert.builder()
                .alertCode("ALT-CO-B")
                .transaction(txn2)
                .rule(testRule)
                .severity(AlertSeverity.MEDIUM)
                .alertStatus(AlertStatus.ASSIGNED)
                .amlCase(caseCoB)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        alertUnassigned = alertRepository.save(Alert.builder()
                .alertCode("ALT-UNASSIGNED")
                .transaction(txn3)
                .rule(testRule)
                .severity(AlertSeverity.LOW)
                .alertStatus(AlertStatus.OPEN)
                .amlCase(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        TenantContext.clear();
    }

    private String generateUserToken(Users user) {
        UserDetails details = customUserDetailsService.loadUserByUsername(user.getEmail());
        Authentication auth = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        return jwtTokenProvider.generateToken(auth);
    }

    @Test
    @DisplayName("CO Onboarding: temporaryPassword is NOT returned in API JSON response and welcome email is sent")
    void coOnboardingSanitizationAndEmailTest() throws Exception {
        ComplianceOfficerRequest request = new ComplianceOfficerRequest();
        request.setUserCode("CO_NEW");
        request.setEmployeeId("EMP100");
        request.setFirstName("New");
        request.setLastName("Officer");
        request.setEmail("newco@hdfc.com");
        request.setPhoneNumber("9988776655");

        MvcResult result = mockMvc.perform(post("/api/v1/bank/admin/add-compliance-officer")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(response.get("userCode").asText()).isEqualTo("CO_NEW");
        assertThat(response.get("email").asText()).isEqualTo("newco@hdfc.com");
        assertThat(response.has("temporaryPassword")).isFalse();

        ArgumentCaptor<String> tempPassCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendComplianceOfficerWelcomeEmail(
                eq("newco@hdfc.com"),
                eq("New"),
                eq("HDFC Bank"),
                eq("hdfc"),
                eq("CO_NEW"),
                tempPassCaptor.capture()
        );

        assertThat(tempPassCaptor.getValue()).isNotNull().startsWith("TmpOff@");
    }

    @Test
    @DisplayName("CO Activation: Bank Admin can activate deactivated CO; non-Bank Admin rejected")
    void coActivationTest() throws Exception {
        // Deactivate CO Alpha
        mockMvc.perform(patch("/api/v1/bank/admin/compliance-officers/" + hdfcCoA.getUserId() + "/deactivate")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken))
                .andExpect(status().isOk());

        Users userInDb = userRepository.findById(hdfcCoA.getUserId()).orElseThrow();
        assertThat(userInDb.getIsActive()).isFalse();

        // Non-Bank Admin (CO) cannot activate
        mockMvc.perform(patch("/api/v1/bank/admin/compliance-officers/" + hdfcCoA.getUserId() + "/activate")
                        .header("Authorization", "Bearer " + hdfcCoBToken))
                .andExpect(status().isForbidden());

        // Bank Admin activates CO Alpha
        MvcResult result = mockMvc.perform(patch("/api/v1/bank/admin/compliance-officers/" + hdfcCoA.getUserId() + "/activate")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("isActive").asBoolean()).isTrue();

        Users reactivatedUser = userRepository.findById(hdfcCoA.getUserId()).orElseThrow();
        assertThat(reactivatedUser.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("Security Authorization: CO endpoints reject Bank Admin and Unauthenticated users")
    void coEndpointsRoleAuthorizationTest() throws Exception {
        // Bank Admin accessing CO dashboard -> 403 Forbidden
        mockMvc.perform(get("/api/v1/compliance/dashboard")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken))
                .andExpect(status().isForbidden());

        // Unauthenticated -> 403 Forbidden
        mockMvc.perform(get("/api/v1/compliance/dashboard"))
                .andExpect(status().isForbidden());

        // CO Alpha -> 200 OK
        mockMvc.perform(get("/api/v1/compliance/dashboard")
                        .header("Authorization", "Bearer " + hdfcCoAToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CO Case Ownership: CO-A lists only assigned cases and cannot view CO-B's case detail")
    void coCaseOwnershipTest() throws Exception {
        // CO Alpha lists cases -> receives caseCoA, does NOT receive caseCoB
        MvcResult listResult = mockMvc.perform(get("/api/v1/compliance/cases")
                        .header("Authorization", "Bearer " + hdfcCoAToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode listResponse = objectMapper.readTree(listResult.getResponse().getContentAsString());
        JsonNode content = listResponse.get("content");

        assertThat(content.size()).isEqualTo(1);
        assertThat(content.get(0).get("caseId").asText()).isEqualTo(caseCoA.getCaseId().toString());

        // CO Alpha gets caseCoA detail -> 200 OK
        mockMvc.perform(get("/api/v1/compliance/cases/" + caseCoA.getCaseId())
                        .header("Authorization", "Bearer " + hdfcCoAToken))
                .andExpect(status().isOk());

        // CO Alpha attempts to get caseCoB detail -> 403 Forbidden / AccessDenied
        mockMvc.perform(get("/api/v1/compliance/cases/" + caseCoB.getCaseId())
                        .header("Authorization", "Bearer " + hdfcCoAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CO Alert Ownership: CO-A lists only assigned alerts and cannot view unassigned or CO-B's alert")
    void coAlertOwnershipTest() throws Exception {
        // CO Alpha lists alerts -> receives alertCoA, does NOT receive alertCoB or alertUnassigned
        MvcResult listResult = mockMvc.perform(get("/api/v1/compliance/alerts")
                        .header("Authorization", "Bearer " + hdfcCoAToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode listResponse = objectMapper.readTree(listResult.getResponse().getContentAsString());
        JsonNode content = listResponse.get("content");

        assertThat(content.size()).isEqualTo(1);
        assertThat(content.get(0).get("alertId").asText()).isEqualTo(alertCoA.getAlertId().toString());

        // CO Alpha gets alertCoA detail -> 200 OK
        mockMvc.perform(get("/api/v1/compliance/alerts/" + alertCoA.getAlertId())
                        .header("Authorization", "Bearer " + hdfcCoAToken))
                .andExpect(status().isOk());

        // CO Alpha attempts to get alertCoB detail -> 403 Forbidden
        mockMvc.perform(get("/api/v1/compliance/alerts/" + alertCoB.getAlertId())
                        .header("Authorization", "Bearer " + hdfcCoAToken))
                .andExpect(status().isForbidden());

        // CO Alpha attempts to get alertUnassigned detail -> 403 Forbidden
        mockMvc.perform(get("/api/v1/compliance/alerts/" + alertUnassigned.getAlertId())
                        .header("Authorization", "Bearer " + hdfcCoAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CO Dashboard: Returns metrics calculated strictly from authenticated CO's work")
    void coDashboardMetricsTest() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/compliance/dashboard")
                        .header("Authorization", "Bearer " + hdfcCoAToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode dashboard = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(dashboard.get("totalAssignedCases").asLong()).isEqualTo(1);
        assertThat(dashboard.get("openCasesCount").asLong()).isEqualTo(1);
        assertThat(dashboard.get("inProgressCasesCount").asLong()).isEqualTo(0);
        assertThat(dashboard.get("escalatedCasesCount").asLong()).isEqualTo(0);
        assertThat(dashboard.get("closedCasesCount").asLong()).isEqualTo(0);
        assertThat(dashboard.get("relatedAlertsCount").asLong()).isEqualTo(1);
    }

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("Phase 2: Start Investigation - Success (OPEN -> IN_PROGRESS) and Audit Trail")
    void startInvestigationSuccessTest() throws Exception {
        TenantContext.setCurrentTenant("tenant_hdfc");

        // CO Alpha starts investigation on caseCoA (OPEN) -> 200 OK
        MvcResult result = mockMvc.perform(post("/api/v1/compliance/cases/" + caseCoA.getCaseId() + "/start-investigation")
                        .header("Authorization", "Bearer " + hdfcCoAToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("status").asText()).isEqualTo("IN_PROGRESS");

        // Verify database state (restore tenant context on test thread after MockMvc execution)
        TenantContext.setCurrentTenant("tenant_hdfc");
        AmlCase caseInDb = caseRepository.findById(caseCoA.getCaseId()).orElseThrow();
        assertThat(caseInDb.getStatus()).isEqualTo(CaseStatus.IN_PROGRESS);

        // Verify Audit Log
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        boolean auditFound = auditLogs.stream().anyMatch(log ->
                "CASE_INVESTIGATION_STARTED".equals(log.getAction()) &&
                "CASE".equals(log.getEntityType()) &&
                caseCoA.getCaseId().toString().equals(log.getEntityId()) &&
                log.getActor() != null && hdfcCoA.getUserId().equals(log.getActor().getUserId())
        );
        assertThat(auditFound).isTrue();

        TenantContext.clear();
    }

    @Test
    @DisplayName("Phase 2: Start Investigation - Ownership Denied (CO-A cannot start CO-B's case)")
    void startInvestigationOwnershipDeniedTest() throws Exception {
        TenantContext.setCurrentTenant("tenant_hdfc");

        // CO Alpha attempts to start caseCoB (assigned to CO Beta) -> 403 Forbidden
        mockMvc.perform(post("/api/v1/compliance/cases/" + caseCoB.getCaseId() + "/start-investigation")
                        .header("Authorization", "Bearer " + hdfcCoAToken))
                .andExpect(status().isForbidden());

        // Verify caseCoB remains OPEN
        TenantContext.setCurrentTenant("tenant_hdfc");
        AmlCase caseInDb = caseRepository.findById(caseCoB.getCaseId()).orElseThrow();
        assertThat(caseInDb.getStatus()).isEqualTo(CaseStatus.OPEN);

        TenantContext.clear();
    }

    @Test
    @DisplayName("Phase 2: Start Investigation - Already IN_PROGRESS rejected with 400 Bad Request")
    void startInvestigationAlreadyInProgressTest() throws Exception {
        TenantContext.setCurrentTenant("tenant_hdfc");

        // First start -> Success
        mockMvc.perform(post("/api/v1/compliance/cases/" + caseCoA.getCaseId() + "/start-investigation")
                        .header("Authorization", "Bearer " + hdfcCoAToken))
                .andExpect(status().isOk());

        // Second start attempt -> 400 Bad Request
        mockMvc.perform(post("/api/v1/compliance/cases/" + caseCoA.getCaseId() + "/start-investigation")
                        .header("Authorization", "Bearer " + hdfcCoAToken))
                .andExpect(status().isBadRequest());

        // Status remains IN_PROGRESS
        TenantContext.setCurrentTenant("tenant_hdfc");
        AmlCase caseInDb = caseRepository.findById(caseCoA.getCaseId()).orElseThrow();
        assertThat(caseInDb.getStatus()).isEqualTo(CaseStatus.IN_PROGRESS);

        TenantContext.clear();
    }

    @Test
    @DisplayName("Phase 2: Start Investigation - Closed case rejected with 400 Bad Request")
    void startInvestigationClosedCaseTest() throws Exception {
        TenantContext.setCurrentTenant("tenant_hdfc");

        // Close the case directly in DB
        caseCoA.setStatus(CaseStatus.CLOSED_NO_ACTION);
        caseRepository.save(caseCoA);

        // Attempt to start investigation -> 400 Bad Request
        mockMvc.perform(post("/api/v1/compliance/cases/" + caseCoA.getCaseId() + "/start-investigation")
                        .header("Authorization", "Bearer " + hdfcCoAToken))
                .andExpect(status().isBadRequest());

        TenantContext.clear();
    }

    @Test
    @DisplayName("Phase 2: Start Investigation - Cross Tenant Isolation Denied")
    void startInvestigationMultitenantDeniedTest() throws Exception {
        // ICICI CO attempts to start HDFC case -> 404 Not Found
        mockMvc.perform(post("/api/v1/compliance/cases/" + caseCoA.getCaseId() + "/start-investigation")
                        .header("Authorization", "Bearer " + iciciCoToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Phase 2: Start Investigation - Authorization Verification (Role Access)")
    void startInvestigationAuthorizationTest() throws Exception {
        // Unauthenticated request -> 403 Forbidden
        mockMvc.perform(post("/api/v1/compliance/cases/" + caseCoA.getCaseId() + "/start-investigation"))
                .andExpect(status().isForbidden());

        // Bank Admin request -> 403 Forbidden
        mockMvc.perform(post("/api/v1/compliance/cases/" + caseCoA.getCaseId() + "/start-investigation")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken))
                .andExpect(status().isForbidden());
    }
}
