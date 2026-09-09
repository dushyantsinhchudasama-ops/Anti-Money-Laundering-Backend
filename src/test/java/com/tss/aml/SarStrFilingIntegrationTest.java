package com.tss.aml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.aml.config.SystemAdminDataInitializer;
import com.tss.aml.dtos.tenant.CaseNoteCreateRequest;
import com.tss.aml.dtos.tenant.CloseCaseNoActionRequest;
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
import com.tss.aml.entities.tenant.SarStr;
import com.tss.aml.entities.tenant.TransactionBatch;
import com.tss.aml.enums.AccountType;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.AlertStatus;
import com.tss.aml.enums.BatchStatus;
import com.tss.aml.enums.CaseStatus;
import com.tss.aml.enums.FiuTypologyCategory;
import com.tss.aml.enums.NoteType;
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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("dev")
public class SarStrFilingIntegrationTest {

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

        // Create Users
        hdfcBankAdmin = userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_ADMIN_SAR")
                .role(UserRole.BANK_ADMIN)
                .email("admin_sar@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("HDFC").lastName("Admin")
                .isActive(true)
                .mustResetPassword(false)
                .build());

        hdfcCoA = userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_CO_A_SAR")
                .role(UserRole.COMPLIANCE_OFFICER)
                .email("coa_sar@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Officer").lastName("A")
                .isActive(true)
                .mustResetPassword(false)
                .build());

        hdfcCoB = userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_CO_B_SAR")
                .role(UserRole.COMPLIANCE_OFFICER)
                .email("cob_sar@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Officer").lastName("B")
                .isActive(true)
                .mustResetPassword(false)
                .build());

        iciciCo = userRepository.save(Users.builder()
                .tenant(tenantIcici)
                .userCode("ICICI_CO_SAR")
                .role(UserRole.COMPLIANCE_OFFICER)
                .email("co_sar@icici.com")
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
                .ruleCode("RULE_SAR_01")
                .ruleName("Structuring Alert Rule SAR")
                .description("Detects transactions just below reporting threshold")
                .typology(RuleTypology.STRUCTURING_SMURFING)
                .parameters(Map.of("amountThreshold", 9000))
                .defaultSeverity(RuleSeverity.HIGH)
                .status(RuleStatus.ACTIVE)
                .build()) : allRules.get(0);

        Account acc = accountRepository.save(Account.builder()
                .accountNumber("ACC-SAR-100")
                .accountHolderName("John Doe")
                .accountType(AccountType.SAVINGS)
                .bankName("HDFC Bank")
                .countryCode("IN")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        TransactionBatch batch = batchRepository.save(TransactionBatch.builder()
                .batchCode("BATCH-SAR-1")
                .uploadedBy(hdfcBankAdmin)
                .fileReference("ref.csv")
                .status(BatchStatus.PROCESSED_NO_ALERTS)
                .uploadedAt(LocalDateTime.now())
                .build());

        FinancialTransaction txn = txnRepository.save(FinancialTransaction.builder()
                .batch(batch)
                .txnNo("TXN-SAR-1")
                .originatorAccount(acc)
                .amount(new BigDecimal("495000.00"))
                .currency("INR")
                .txnType(TransactionType.NEFT)
                .direction(TransactionDirection.OUT)
                .countryCode("IN")
                .txnTimestamp(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        Alert alert1 = alertRepository.save(Alert.builder()
                .alertCode("ALT-SAR-1")
                .transaction(txn)
                .rule(structRule)
                .severity(AlertSeverity.HIGH)
                .alertStatus(AlertStatus.ASSIGNED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        inProgressCaseWithNotes = caseRepository.save(AmlCase.builder()
                .caseCode("CASE-SAR-WITHNOTES")
                .createdBy(hdfcBankAdmin)
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
                .content("Detailed investigation completed. Multiple suspicious transfers identified.")
                .createdAt(LocalDateTime.now())
                .build());

        inProgressCaseWithoutNotes = caseRepository.save(AmlCase.builder()
                .caseCode("CASE-SAR-NONOTES")
                .createdBy(hdfcBankAdmin)
                .assignedTo(hdfcCoA)
                .status(CaseStatus.IN_PROGRESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        openCase = caseRepository.save(AmlCase.builder()
                .caseCode("CASE-SAR-OPEN")
                .createdBy(hdfcBankAdmin)
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
    @DisplayName("1. PREVIEW READ-ONLY: GET preview is strictly read-only and mutates no database state")
    void previewReadOnlyTest() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/sar-str/preview")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId", equalTo(inProgressCaseWithNotes.getCaseId().toString())))
                .andExpect(jsonPath("$.caseCode", equalTo("CASE-SAR-WITHNOTES")))
                .andExpect(jsonPath("$.caseStatus", equalTo("IN_PROGRESS")))
                .andExpect(jsonPath("$.accountNumber", equalTo("ACC-SAR-100")))
                .andExpect(jsonPath("$.supportedReportTypes", hasItems("SAR", "STR")))
                .andExpect(jsonPath("$.supportedTypologyCategories", hasItem("STRUCTURING")));

        // Verify zero database mutation
        TenantContext.setCurrentTenant("tenant_hdfc");
        AmlCase unchangedCase = caseRepository.findById(inProgressCaseWithNotes.getCaseId()).orElseThrow();
        assertEquals(CaseStatus.IN_PROGRESS, unchangedCase.getStatus());
        assertEquals(0, sarStrRepository.count());
        assertEquals(0, auditLogRepository.count());
        TenantContext.clear();
    }

    @Test
    @DisplayName("2. SUCCESSFUL FILING: CO can file SAR/STR, resulting in reference, BYTEA PDF, status CLOSED_SAR_FILED, and audit record")
    void successfulSarStrFilingTest() throws Exception {
        SarStrFilingRequest req = SarStrFilingRequest.builder()
                .reportType(SarStrType.SAR)
                .typologyCategory(FiuTypologyCategory.STRUCTURING)
                .descriptionOfActivity("Multiple transactions structured under reporting threshold.")
                .basisForSuspicion("Pattern of high velocity cash deposits and immediate outgoing wire transfers.")
                .supportingEvidence("Bank statements, transaction ledger TXN-SAR-1.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/sar-str")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sarStrId", notNullValue()))
                .andExpect(jsonPath("$.caseId", equalTo(inProgressCaseWithNotes.getCaseId().toString())))
                .andExpect(jsonPath("$.reportType", equalTo("SAR")))
                .andExpect(jsonPath("$.typologyCategory", equalTo("STRUCTURING")))
                .andExpect(jsonPath("$.referenceNumber", startsWith("SAR-2026-")))
                .andExpect(jsonPath("$.pdfReference", endsWith(".pdf")))
                .andExpect(jsonPath("$.filedByEmail", equalTo("coa_sar@hdfc.com")));

        TenantContext.setCurrentTenant("tenant_hdfc");
        AmlCase updatedCase = caseRepository.findById(inProgressCaseWithNotes.getCaseId()).orElseThrow();
        assertEquals(CaseStatus.CLOSED_SAR_FILED, updatedCase.getStatus());
        assertNotNull(updatedCase.getClosedAt());

        SarStr savedReport = sarStrRepository.findByAmlCase_CaseId(inProgressCaseWithNotes.getCaseId()).orElseThrow();
        assertNotNull(savedReport.getPdfContent());
        assertTrue(savedReport.getPdfContent().length > 0);

        List<AuditLog> auditLogs = auditLogRepository.findAll();
        boolean auditFound = auditLogs.stream()
                .anyMatch(log -> "SAR_STR_FILED".equals(log.getAction()) && log.getEntityId().equals(savedReport.getSarStrId().toString()));
        assertTrue(auditFound, "Expected SAR_STR_FILED audit log entry");
        TenantContext.clear();
    }

    @Test
    @DisplayName("3. PREREQUISITE: Cannot file SAR/STR without investigation notes")
    void investigationNotePrerequisiteTest() throws Exception {
        SarStrFilingRequest req = SarStrFilingRequest.builder()
                .reportType(SarStrType.STR)
                .typologyCategory(FiuTypologyCategory.LAYERING)
                .descriptionOfActivity("Layering activity via foreign accounts.")
                .basisForSuspicion("Rapid wire movements.")
                .supportingEvidence("SWIFT messages.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithoutNotes.getCaseId() + "/sar-str")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        TenantContext.setCurrentTenant("tenant_hdfc");
        AmlCase caseAfterFailedFiling = caseRepository.findById(inProgressCaseWithoutNotes.getCaseId()).orElseThrow();
        assertEquals(CaseStatus.IN_PROGRESS, caseAfterFailedFiling.getStatus());
        assertEquals(0, sarStrRepository.count());
        TenantContext.clear();
    }

    @Test
    @DisplayName("4. MANDATORY FIELDS: Filing fails if mandatory fields are missing")
    void mandatoryFieldsValidationTest() throws Exception {
        SarStrFilingRequest invalidReq = SarStrFilingRequest.builder()
                .reportType(null) // Missing report type
                .typologyCategory(FiuTypologyCategory.STRUCTURING)
                .descriptionOfActivity("")
                .basisForSuspicion("Some basis")
                .supportingEvidence("Some evidence")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/sar-str")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("5. OWNERSHIP ENFORCEMENT: CO-B cannot file SAR/STR for CO-A's case")
    void caseOwnershipEnforcementTest() throws Exception {
        SarStrFilingRequest req = SarStrFilingRequest.builder()
                .reportType(SarStrType.SAR)
                .typologyCategory(FiuTypologyCategory.PEP_TRANSACTION)
                .descriptionOfActivity("Unauthorized filing attempt.")
                .basisForSuspicion("Suspicion basis.")
                .supportingEvidence("Evidence.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/sar-str")
                        .header("Authorization", "Bearer " + hdfcCoBToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("6. DUPLICATE FILING: Cannot file SAR/STR twice for the same case")
    void duplicateFilingPreventionTest() throws Exception {
        SarStrFilingRequest req = SarStrFilingRequest.builder()
                .reportType(SarStrType.SAR)
                .typologyCategory(FiuTypologyCategory.STRUCTURING)
                .descriptionOfActivity("First filing.")
                .basisForSuspicion("Basis.")
                .supportingEvidence("Evidence.")
                .build();

        // First filing -> success
        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/sar-str")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Second filing -> rejected
        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/sar-str")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("7. CLOSED-CASE IMMUTABILITY: Cannot add notes, restart, or close-no-action once CLOSED_SAR_FILED")
    void closedCaseImmutabilityTest() throws Exception {
        SarStrFilingRequest req = SarStrFilingRequest.builder()
                .reportType(SarStrType.SAR)
                .typologyCategory(FiuTypologyCategory.VELOCITY_CHECK)
                .descriptionOfActivity("Filing to test immutability.")
                .basisForSuspicion("Velocity anomaly.")
                .supportingEvidence("Audit logs.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/sar-str")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // 1. Add note attempt -> fails
        CaseNoteCreateRequest noteReq = CaseNoteCreateRequest.builder()
                .noteType(NoteType.OBSERVATION)
                .content("Attempting note on closed SAR case")
                .build();
        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/notes")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noteReq)))
                .andExpect(status().is4xxClientError());

        // 2. Restart investigation attempt -> fails
        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/start-investigation")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc"))
                .andExpect(status().is4xxClientError());

        // 3. Close no-action attempt -> fails
        CloseCaseNoActionRequest closeReq = CloseCaseNoActionRequest.builder().rationale("Closing again.").build();
        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/close-no-action")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(closeReq)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("8 & 9. PDF DOWNLOAD & BANK ADMIN FILING LOG: CO and Bank Admin can download PDF, Admin views filing log")
    void pdfRetrievalAndFilingLogTest() throws Exception {
        SarStrFilingRequest req = SarStrFilingRequest.builder()
                .reportType(SarStrType.STR)
                .typologyCategory(FiuTypologyCategory.FRAUD_RELATED_ML)
                .descriptionOfActivity("Filing for PDF download and admin log verification.")
                .basisForSuspicion("Fraud related money laundering.")
                .supportingEvidence("Transaction history.")
                .build();

        mockMvc.perform(post("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/sar-str")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        TenantContext.setCurrentTenant("tenant_hdfc");
        SarStr savedReport = sarStrRepository.findByAmlCase_CaseId(inProgressCaseWithNotes.getCaseId()).orElseThrow();
        TenantContext.clear();

        // CO downloads PDF
        mockMvc.perform(get("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/sar-str/pdf")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", equalTo("application/pdf")));

        // Bank Admin retrieves institutional filing log
        mockMvc.perform(get("/api/v1/bank/admin/sar-str")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .header("X-Tenant-ID", "tenant_hdfc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sarStrId", equalTo(savedReport.getSarStrId().toString())))
                .andExpect(jsonPath("$.content[0].reportType", equalTo("STR")));

        // Bank Admin downloads PDF via admin endpoint
        mockMvc.perform(get("/api/v1/bank/admin/sar-str/" + savedReport.getSarStrId() + "/pdf")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .header("X-Tenant-ID", "tenant_hdfc"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", equalTo("application/pdf")));
    }

    @Test
    @DisplayName("10. CROSS-TENANT ISOLATION: CO from another tenant receives 404 Not Found")
    void crossTenantIsolationTest() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/cases/" + inProgressCaseWithNotes.getCaseId() + "/sar-str/preview")
                        .header("Authorization", "Bearer " + iciciCoToken)
                        .header("X-Tenant-ID", "tenant_icici"))
                .andExpect(status().isNotFound());
    }
}
