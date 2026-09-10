package com.tss.aml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.aml.config.SystemAdminDataInitializer;
import com.tss.aml.dtos.tenant.CaseNoteCreateRequest;
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
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("dev")
public class CaseNotesIntegrationTest {

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

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

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

        jdbcTemplate.execute("TRUNCATE TABLE public.users CASCADE");

        // Create Users
        hdfcBankAdmin = userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_ADMIN_NOTE")
                .role(UserRole.BANK_ADMIN)
                .email("admin@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("HDFC").lastName("Admin")
                .isActive(true)
                .mustResetPassword(false)
                .build());

        hdfcCoA = userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_CO_A_NOTE")
                .role(UserRole.COMPLIANCE_OFFICER)
                .email("coa@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Officer").lastName("A")
                .isActive(true)
                .mustResetPassword(false)
                .build());

        hdfcCoB = userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_CO_B_NOTE")
                .role(UserRole.COMPLIANCE_OFFICER)
                .email("cob@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Officer").lastName("B")
                .isActive(true)
                .mustResetPassword(false)
                .build());

        iciciCo = userRepository.save(Users.builder()
                .tenant(tenantIcici)
                .userCode("ICICI_CO_NOTE")
                .role(UserRole.COMPLIANCE_OFFICER)
                .email("co@icici.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("ICICI").lastName("Officer")
                .isActive(true)
                .mustResetPassword(false)
                .build());

        // Generate Tokens
        hdfcBankAdminToken = generateUserToken(hdfcBankAdmin);
        hdfcCoAToken = generateUserToken(hdfcCoA);
        hdfcCoBToken = generateUserToken(hdfcCoB);
        iciciCoToken = generateUserToken(iciciCo);

        // Setup Rule
        TenantContext.setCurrentTenant("tenant_hdfc");

        Rule testRule = ruleRepository.findAll().stream().findFirst()
                .orElseGet(() -> ruleRepository.save(Rule.builder()
                        .ruleCode("CTR-NOTE-001")
                        .ruleName("High Value Transaction")
                        .description("Transaction exceeding threshold")
                        .typology(RuleTypology.LAYERING)
                        .parameters(Map.of())
                        .defaultSeverity(RuleSeverity.HIGH)
                        .status(RuleStatus.ACTIVE)
                        .build()));

        Account acc = accountRepository.save(Account.builder()
                .accountNumber("ACC-NOTES-1")
                .accountHolderName("John Doe")
                .accountType(AccountType.SAVINGS)
                .countryCode("IN")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        TransactionBatch batch = batchRepository.save(TransactionBatch.builder()
                .batchCode("BATCH-NOTES-1")
                .uploadedBy(hdfcBankAdmin)
                .fileReference("ref.csv")
                .status(BatchStatus.PROCESSED_NO_ALERTS)
                .uploadedAt(LocalDateTime.now())
                .build());

        FinancialTransaction txn = txnRepository.save(FinancialTransaction.builder()
                .batch(batch)
                .txnNo("TXN-NOTES-1")
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
                .alertCode("ALT-NOTES-1")
                .transaction(txn)
                .rule(testRule)
                .severity(AlertSeverity.HIGH)
                .alertStatus(AlertStatus.ASSIGNED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        openCase = caseRepository.save(AmlCase.builder()
                .caseCode("CASE-NOTES-OPEN")
                .createdBy(hdfcBankAdmin)
                .assignedTo(hdfcCoA)
                .status(CaseStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        alert1.setAmlCase(openCase);
        alertRepository.save(alert1);

        unassignedCase = caseRepository.save(AmlCase.builder()
                .caseCode("CASE-NOTES-UNASSIGN")
                .createdBy(hdfcBankAdmin)
                .assignedTo(null)
                .status(CaseStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        TenantContext.clear();
    }

    private String generateUserToken(Users user) {
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(user.getEmail());
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        return jwtTokenProvider.generateToken(auth);
    }

    @Test
    @DisplayName("NOTE CREATION & AUTO-TRANSITION: Create OBSERVATION note auto-transitions OPEN case to IN_PROGRESS with single CASE_INVESTIGATION_STARTED audit")
    void createObservationNoteSuccessAndAutoTransitionStatusTest() throws Exception {
        CaseNoteCreateRequest request = CaseNoteCreateRequest.builder()
                .noteType(NoteType.OBSERVATION)
                .content("Suspicious high velocity transfer observed in customer account.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + openCase.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteId", notNullValue()))
                .andExpect(jsonPath("$.noteType", equalTo("OBSERVATION")))
                .andExpect(jsonPath("$.content", equalTo("Suspicious high velocity transfer observed in customer account.")))
                .andExpect(jsonPath("$.authorId", equalTo(hdfcCoA.getUserId().toString())))
                .andExpect(jsonPath("$.authorEmail", equalTo("coa@hdfc.com")))
                .andExpect(jsonPath("$.createdAt", notNullValue()));

        // Verify automatic OPEN -> IN_PROGRESS transition and single CASE_INVESTIGATION_STARTED audit
        TenantContext.setCurrentTenant("tenant_hdfc");
        AmlCase updatedCase = caseRepository.findById(openCase.getCaseId()).orElseThrow();
        assertEquals(CaseStatus.IN_PROGRESS, updatedCase.getStatus());

        List<AuditLog> startAudits = auditLogRepository.findAll().stream()
                .filter(l -> "CASE_INVESTIGATION_STARTED".equals(l.getAction()))
                .collect(Collectors.toList());
        assertEquals(1, startAudits.size());
        assertEquals(openCase.getCaseId().toString(), startAudits.get(0).getEntityId());

        List<AuditLog> noteAudits = auditLogRepository.findAll().stream()
                .filter(l -> "CASE_NOTE_ADDED".equals(l.getAction()))
                .collect(Collectors.toList());
        assertEquals(1, noteAudits.size());
        assertEquals(openCase.getCaseId().toString(), noteAudits.get(0).getEntityId());
        TenantContext.clear();
    }

    @Test
    @DisplayName("NOTE TYPES & REPEATED NOTES: Adding notes to IN_PROGRESS case does not create duplicate CASE_INVESTIGATION_STARTED audit")
    void createEvidenceReferenceAndDecisionRationaleNotesTest() throws Exception {
        // First note transitions OPEN -> IN_PROGRESS
        CaseNoteCreateRequest request1 = CaseNoteCreateRequest.builder()
                .noteType(NoteType.EVIDENCE_REFERENCE)
                .content("Referenced bank statement document Ref-9988.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + openCase.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteType", equalTo("EVIDENCE_REFERENCE")));

        // Second note on already IN_PROGRESS case (does not produce second CASE_INVESTIGATION_STARTED)
        CaseNoteCreateRequest request2 = CaseNoteCreateRequest.builder()
                .noteType(NoteType.DECISION_RATIONALE)
                .content("Escalation rationale based on international wire destination.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + openCase.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteType", equalTo("DECISION_RATIONALE")));

        TenantContext.setCurrentTenant("tenant_hdfc");
        List<AuditLog> startAudits = auditLogRepository.findAll().stream()
                .filter(l -> "CASE_INVESTIGATION_STARTED".equals(l.getAction()))
                .collect(Collectors.toList());
        assertEquals(1, startAudits.size(), "Only one investigation start audit should exist");

        List<AuditLog> noteAudits = auditLogRepository.findAll().stream()
                .filter(l -> "CASE_NOTE_ADDED".equals(l.getAction()))
                .collect(Collectors.toList());
        assertEquals(2, noteAudits.size());
        TenantContext.clear();
    }

    @Test
    @DisplayName("NOTE RETRIEVAL: Notes are returned in chronological order")
    void getNotesInChronologicalOrderTest() throws Exception {
        // Add 2 notes
        CaseNoteCreateRequest req1 = CaseNoteCreateRequest.builder().noteType(NoteType.OBSERVATION).content("First note").build();
        CaseNoteCreateRequest req2 = CaseNoteCreateRequest.builder().noteType(NoteType.EVIDENCE_REFERENCE).content("Second note").build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + openCase.getCaseId() + "/notes")
                .header("Authorization", "Bearer " + hdfcCoAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1))).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/compliance/cases/" + openCase.getCaseId() + "/notes")
                .header("Authorization", "Bearer " + hdfcCoAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2))).andExpect(status().isOk());

        // Retrieve notes
        mockMvc.perform(get("/api/v1/compliance/cases/" + openCase.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + hdfcCoAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].content", equalTo("First note")))
                .andExpect(jsonPath("$[1].content", equalTo("Second note")));
    }

    @Test
    @DisplayName("OWNERSHIP ENFORCEMENT: Note creation by unassigned CO or on unassigned case is rejected")
    void noteCreationByOtherCOOrUnassignedCaseRejectedTest() throws Exception {
        CaseNoteCreateRequest req = CaseNoteCreateRequest.builder().noteType(NoteType.OBSERVATION).content("Test note").build();

        // CO-B trying to add note to CO-A's case
        mockMvc.perform(post("/api/v1/compliance/cases/" + openCase.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + hdfcCoBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // CO-A trying to add note to unassigned case
        mockMvc.perform(post("/api/v1/compliance/cases/" + unassignedCase.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("OWNERSHIP ENFORCEMENT: Note retrieval by unassigned CO or on unassigned case is rejected")
    void getNotesByOtherCOOrUnassignedCaseRejectedTest() throws Exception {
        // CO-B trying to get notes of CO-A's case
        mockMvc.perform(get("/api/v1/compliance/cases/" + openCase.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + hdfcCoBToken))
                .andExpect(status().isForbidden());

        // CO-A trying to get notes of unassigned case
        mockMvc.perform(get("/api/v1/compliance/cases/" + unassignedCase.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + hdfcCoAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("INPUT VALIDATION: Missing noteType or blank content is rejected with 400 Bad Request")
    void validationErrorsTest() throws Exception {
        // Missing noteType
        CaseNoteCreateRequest missingTypeReq = CaseNoteCreateRequest.builder().content("Content").build();
        mockMvc.perform(post("/api/v1/compliance/cases/" + openCase.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingTypeReq)))
                .andExpect(status().isBadRequest());

        // Blank content
        CaseNoteCreateRequest blankContentReq = CaseNoteCreateRequest.builder().noteType(NoteType.OBSERVATION).content("   ").build();
        mockMvc.perform(post("/api/v1/compliance/cases/" + openCase.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankContentReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ROLE & SECURITY: Non-CO users and unauthenticated requests are rejected")
    void roleSecurityAndUnauthenticatedTest() throws Exception {
        CaseNoteCreateRequest req = CaseNoteCreateRequest.builder().noteType(NoteType.OBSERVATION).content("Note").build();

        // Bank Admin cannot add note
        mockMvc.perform(post("/api/v1/compliance/cases/" + openCase.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // Unauthenticated access
        mockMvc.perform(post("/api/v1/compliance/cases/" + openCase.getCaseId() + "/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CROSS-TENANT ISOLATION: Compliance Officer from another tenant cannot access case notes")
    void crossTenantAccessRejectedTest() throws Exception {
        CaseNoteCreateRequest req = CaseNoteCreateRequest.builder().noteType(NoteType.OBSERVATION).content("Note").build();

        // ICICI CO trying to add note to HDFC case
        mockMvc.perform(post("/api/v1/compliance/cases/" + openCase.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + iciciCoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());

        // ICICI CO trying to read HDFC case notes
        mockMvc.perform(get("/api/v1/compliance/cases/" + openCase.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + iciciCoToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("REASSIGNMENT INTERACTION: Reassigned case retains note history with original author, accessible to new CO, revoked from previous CO")
    void reassignmentInteractionNotesPreservationTest() throws Exception {
        // CO-A creates a note
        CaseNoteCreateRequest req = CaseNoteCreateRequest.builder()
                .noteType(NoteType.OBSERVATION)
                .content("Note created by CO-A before reassignment.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + openCase.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Bank Admin reassigns case from CO-A to CO-B
        String reassignJson = "{\"newAssigneeId\":\"" + hdfcCoB.getUserId() + "\",\"reason\":\"Shift change\"}";
        mockMvc.perform(post("/api/v1/bank/admin/cases/" + openCase.getCaseId() + "/reassign")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reassignJson))
                .andExpect(status().isOk());

        // CO-B (new owner) can retrieve note history, retaining CO-A as original author
        mockMvc.perform(get("/api/v1/compliance/cases/" + openCase.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + hdfcCoBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content", equalTo("Note created by CO-A before reassignment.")))
                .andExpect(jsonPath("$[0].authorId", equalTo(hdfcCoA.getUserId().toString())))
                .andExpect(jsonPath("$[0].authorEmail", equalTo("coa@hdfc.com")));

        // CO-A (previous owner) can NO LONGER access the case or notes
        mockMvc.perform(get("/api/v1/compliance/cases/" + openCase.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + hdfcCoAToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/compliance/cases/" + openCase.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
