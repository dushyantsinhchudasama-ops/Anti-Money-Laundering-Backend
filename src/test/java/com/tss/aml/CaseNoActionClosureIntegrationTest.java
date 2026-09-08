package com.tss.aml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.aml.config.SystemAdminDataInitializer;
import com.tss.aml.dtos.tenant.CaseNoteCreateRequest;
import com.tss.aml.dtos.tenant.CloseCaseNoActionRequest;
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
import com.tss.aml.enums.NoteType;
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
import com.tss.aml.repositories.CaseNoteRepository;
import com.tss.aml.repositories.FinancialTransactionRepository;
import com.tss.aml.repositories.NotificationRepository;
import com.tss.aml.repositories.RuleRepository;
import com.tss.aml.repositories.SystemAdminRepository;
import com.tss.aml.repositories.TenantRepository;
import com.tss.aml.repositories.TransactionBatchRepository;
import com.tss.aml.repositories.UserRepository;
import com.tss.aml.security.CustomUserDetailsService;
import com.tss.aml.security.JwtTokenProvider;
import com.tss.aml.services.TenantMigrationService;
import com.tss.aml.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("dev")
public class CaseNoActionClosureIntegrationTest {

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

    private Users hdfcBankAdmin;
    private Users hdfcCoA;
    private Users hdfcCoB;
    private Users iciciCo;

    private String hdfcBankAdminToken;
    private String hdfcCoAToken;
    private String hdfcCoBToken;
    private String iciciCoToken;

    private Tenant tenantHdfc;
    private Tenant tenantIcici;

    private AmlCase inProgressCaseWithNotes;
    private AmlCase inProgressCaseWithoutNotes;
    private AmlCase openCase;
    private AmlCase unassignedCase;

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
        caseNoteRepository.deleteAll();
        auditLogRepository.deleteAll();
        alertRepository.deleteAll();
        caseRepository.deleteAll();
        txnRepository.deleteAll();
        batchRepository.deleteAll();
        accountRepository.deleteAll();
        TenantContext.clear();

        userRepository.deleteAll();

        // Create Users
        hdfcBankAdmin = userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_ADMIN_CLOSE")
                .role(UserRole.BANK_ADMIN)
                .email("admin_close@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("HDFC").lastName("Admin")
                .isActive(true)
                .mustResetPassword(false)
                .build());

        hdfcCoA = userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_CO_A_CLOSE")
                .role(UserRole.COMPLIANCE_OFFICER)
                .email("coa_close@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Officer").lastName("A")
                .isActive(true)
                .mustResetPassword(false)
                .build());

        hdfcCoB = userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_CO_B_CLOSE")
                .role(UserRole.COMPLIANCE_OFFICER)
                .email("cob_close@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Officer").lastName("B")
                .isActive(true)
                .mustResetPassword(false)
                .build());

        iciciCo = userRepository.save(Users.builder()
                .tenant(tenantIcici)
                .userCode("ICICI_CO_CLOSE")
                .role(UserRole.COMPLIANCE_OFFICER)
                .email("co_close@icici.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("ICICI").lastName("Officer")
                .isActive(true)
                .mustResetPassword(false)
                .build());

        hdfcBankAdminToken = generateToken(hdfcBankAdmin.getEmail(), "tenant_hdfc");
        hdfcCoAToken = generateToken(hdfcCoA.getEmail(), "tenant_hdfc");
        hdfcCoBToken = generateToken(hdfcCoB.getEmail(), "tenant_hdfc");
        iciciCoToken = generateToken(iciciCo.getEmail(), "tenant_icici");

        // Seed HDFC domain entities
        TenantContext.setCurrentTenant("tenant_hdfc");

        List<Rule> allRules = ruleRepository.findAll();
        Rule structRule = allRules.isEmpty() ? ruleRepository.save(Rule.builder()
                .ruleCode("RULE_CLOSE_01")
                .ruleName("Structuring Alert Rule Close")
                .description("Detects transactions just below reporting threshold")
                .typology(RuleTypology.STRUCTURING_SMURFING)
                .parameters(Map.of("amountThreshold", 9000))
                .defaultSeverity(RuleSeverity.HIGH)
                .status(RuleStatus.ACTIVE)
                .build()) : allRules.get(0);

        Account acc = accountRepository.save(Account.builder()
                .accountNumber("ACC-CLOSE-1")
                .accountHolderName("Jane Doe")
                .accountType(AccountType.SAVINGS)
                .countryCode("IN")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        TransactionBatch batch = batchRepository.save(TransactionBatch.builder()
                .batchCode("BATCH-CLOSE-1")
                .uploadedBy(hdfcBankAdmin)
                .fileReference("ref.csv")
                .status(BatchStatus.PROCESSED_NO_ALERTS)
                .uploadedAt(LocalDateTime.now())
                .build());

        FinancialTransaction txn = txnRepository.save(FinancialTransaction.builder()
                .batch(batch)
                .txnNo("TXN-CLOSE-1")
                .originatorAccount(acc)
                .amount(new BigDecimal("15000.00"))
                .currency("INR")
                .txnType(TransactionType.NEFT)
                .direction(TransactionDirection.OUT)
                .countryCode("IN")
                .txnTimestamp(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        Alert alert1 = alertRepository.save(Alert.builder()
                .alertCode("ALT-CLOSE-1")
                .transaction(txn)
                .rule(structRule)
                .severity(AlertSeverity.HIGH)
                .alertStatus(AlertStatus.ASSIGNED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        inProgressCaseWithNotes = caseRepository.save(AmlCase.builder()
                .caseCode("CASE-CLOSE-WITHNOTES")
                .createdBy(hdfcBankAdmin)
                .assignedTo(hdfcCoA)
                .status(CaseStatus.IN_PROGRESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        alert1.setAmlCase(inProgressCaseWithNotes);
        alertRepository.save(alert1);

        // Add a note to inProgressCaseWithNotes
        caseNoteRepository.save(com.tss.aml.entities.tenant.CaseNote.builder()
                .amlCase(inProgressCaseWithNotes)
                .author(hdfcCoA)
                .noteType(NoteType.OBSERVATION)
                .content("Initial investigation note")
                .createdAt(LocalDateTime.now())
                .build());

        inProgressCaseWithoutNotes = caseRepository.save(AmlCase.builder()
                .caseCode("CASE-CLOSE-NONOTES")
                .createdBy(hdfcBankAdmin)
                .assignedTo(hdfcCoA)
                .status(CaseStatus.IN_PROGRESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        openCase = caseRepository.save(AmlCase.builder()
                .caseCode("CASE-CLOSE-OPEN")
                .createdBy(hdfcBankAdmin)
                .assignedTo(hdfcCoA)
                .status(CaseStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        unassignedCase = caseRepository.save(AmlCase.builder()
                .caseCode("CASE-CLOSE-UNASSIGN")
                .createdBy(hdfcBankAdmin)
                .assignedTo(null)
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
    @DisplayName("1. SUCCESSFUL CLOSURE: Assigned CO can close IN_PROGRESS case with rationale")
    void successfulClosureTest() throws Exception {
        CloseCaseNoActionRequest req = CloseCaseNoActionRequest.builder()
                .rationale("Transaction activity is consistent with normal customer business activity.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/close-no-action")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId", equalTo(inProgressCaseWithNotes.getCaseId().toString())))
                .andExpect(jsonPath("$.status", equalTo("CLOSED_NO_ACTION")))
                .andExpect(jsonPath("$.falsePositiveRationale", equalTo("Transaction activity is consistent with normal customer business activity.")))
                .andExpect(jsonPath("$.closedAt", notNullValue()));

        TenantContext.setCurrentTenant("tenant_hdfc");
        AmlCase updatedCase = caseRepository.findById(inProgressCaseWithNotes.getCaseId()).orElseThrow();
        assertEquals(CaseStatus.CLOSED_NO_ACTION, updatedCase.getStatus());
        assertEquals("Transaction activity is consistent with normal customer business activity.", updatedCase.getFalsePositiveRationale());
        assertNotNull(updatedCase.getClosedAt());

        List<AuditLog> auditLogs = auditLogRepository.findAll();
        boolean auditFound = auditLogs.stream()
                .anyMatch(log -> "CASE_CLOSED_NO_ACTION".equals(log.getAction()) && log.getEntityId().equals(inProgressCaseWithNotes.getCaseId().toString()));
        assertTrue(auditFound, "Expected CASE_CLOSED_NO_ACTION audit log entry");
        TenantContext.clear();
    }

    @Test
    @DisplayName("2. PREREQUISITE: Cannot close case without at least one investigation note")
    void investigationNotePrerequisiteTest() throws Exception {
        CloseCaseNoActionRequest req = CloseCaseNoActionRequest.builder()
                .rationale("Closing without notes should fail.")
                .build();

        // Attempt closing case without notes
        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithoutNotes.getCaseId() + "/close-no-action")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        // Verify case remains IN_PROGRESS
        TenantContext.setCurrentTenant("tenant_hdfc");
        AmlCase caseAfterFailedClose = caseRepository.findById(inProgressCaseWithoutNotes.getCaseId()).orElseThrow();
        assertEquals(CaseStatus.IN_PROGRESS, caseAfterFailedClose.getStatus());

        // Now add a note
        CaseNoteCreateRequest noteReq = CaseNoteCreateRequest.builder()
                .noteType(NoteType.OBSERVATION)
                .content("Added note to satisfy prerequisite")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithoutNotes.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noteReq)))
                .andExpect(status().isOk());

        // Retry closure -> should succeed
        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithoutNotes.getCaseId() + "/close-no-action")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("CLOSED_NO_ACTION")));

        TenantContext.clear();
    }

    @Test
    @DisplayName("3. MANDATORY RATIONALE: Blank or missing rationale is rejected with 400")
    void mandatoryRationaleTest() throws Exception {
        // Missing rationale
        CloseCaseNoActionRequest reqBlank = CloseCaseNoActionRequest.builder().rationale("   ").build();
        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/close-no-action")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqBlank)))
                .andExpect(status().isBadRequest());

        // Verify case is not closed
        TenantContext.setCurrentTenant("tenant_hdfc");
        AmlCase c = caseRepository.findById(inProgressCaseWithNotes.getCaseId()).orElseThrow();
        assertEquals(CaseStatus.IN_PROGRESS, c.getStatus());
        TenantContext.clear();
    }

    @Test
    @DisplayName("4. OWNERSHIP ENFORCEMENT: CO-B cannot close CO-A's case")
    void ownershipEnforcementTest() throws Exception {
        CloseCaseNoActionRequest req = CloseCaseNoActionRequest.builder()
                .rationale("Unauthorized attempt.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/close-no-action")
                        .header("Authorization", "Bearer " + hdfcCoBToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        TenantContext.setCurrentTenant("tenant_hdfc");
        AmlCase c = caseRepository.findById(inProgressCaseWithNotes.getCaseId()).orElseThrow();
        assertEquals(CaseStatus.IN_PROGRESS, c.getStatus());
        TenantContext.clear();
    }

    @Test
    @DisplayName("5. UNASSIGNED CASE: Unassigned case closure is denied")
    void unassignedCaseTest() throws Exception {
        CloseCaseNoActionRequest req = CloseCaseNoActionRequest.builder()
                .rationale("Unassigned case attempt.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + unassignedCase.getCaseId() + "/close-no-action")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("6. INVALID STATUS TRANSITION: Cannot close OPEN case directly or already closed case")
    void invalidStatusTransitionTest() throws Exception {
        CloseCaseNoActionRequest req = CloseCaseNoActionRequest.builder()
                .rationale("Attempting to close OPEN case directly.")
                .build();

        // OPEN case closure attempt
        mockMvc.perform(post("/api/v1/compliance/cases/" + openCase.getCaseId() + "/close-no-action")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());

        // Close inProgressCaseWithNotes successfully first
        CloseCaseNoActionRequest validReq = CloseCaseNoActionRequest.builder()
                .rationale("Valid rationale.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/close-no-action")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk());

        // Attempting to close again
        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/close-no-action")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("7 & 8. ROLE & SECURITY: Bank Admin and Unauthenticated requests are rejected")
    void roleAndSecurityTest() throws Exception {
        CloseCaseNoActionRequest req = CloseCaseNoActionRequest.builder()
                .rationale("Security test rationale.")
                .build();

        // Bank Admin
        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/close-no-action")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // Unauthenticated
        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/close-no-action")
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("9. CROSS-TENANT ISOLATION: CO from another tenant receives 404 Not Found")
    void crossTenantIsolationTest() throws Exception {
        CloseCaseNoActionRequest req = CloseCaseNoActionRequest.builder()
                .rationale("Cross tenant attempt.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/close-no-action")
                        .header("Authorization", "Bearer " + iciciCoToken)
                        .header("X-Tenant-ID", "tenant_icici")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("10, 11 & 12. CLOSED-CASE PROTECTION: Cannot add note or restart investigation post-closure")
    void closedCaseProtectionTest() throws Exception {
        CloseCaseNoActionRequest closeReq = CloseCaseNoActionRequest.builder()
                .rationale("Closing case for protection verification.")
                .build();

        // Close the case
        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/close-no-action")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(closeReq)))
                .andExpect(status().isOk());

        // Attempt to add new note post-closure
        CaseNoteCreateRequest noteReq = CaseNoteCreateRequest.builder()
                .noteType(NoteType.OBSERVATION)
                .content("Post-closure note attempt")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noteReq)))
                .andExpect(status().is4xxClientError());

        // Attempt to restart investigation post-closure
        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/start-investigation")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc"))
                .andExpect(status().is4xxClientError());
    }
}
