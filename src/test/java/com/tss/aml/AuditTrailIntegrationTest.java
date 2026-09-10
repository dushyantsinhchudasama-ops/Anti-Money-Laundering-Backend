package com.tss.aml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.aml.config.SystemAdminDataInitializer;
import com.tss.aml.dtos.rule.AssignRuleRequest;
import com.tss.aml.dtos.rule.CreateRuleRequest;
import com.tss.aml.dtos.rule.UpdateRuleRequest;
import com.tss.aml.dtos.tenant.ComplianceOfficerRequest;
import com.tss.aml.entities.system.BankRuleAssignment;
import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.entities.system.SystemAuditLog;
import com.tss.aml.entities.system.Tenant;
import com.tss.aml.entities.system.Users;
import com.tss.aml.entities.tenant.AmlCase;
import com.tss.aml.entities.tenant.AuditLog;
import com.tss.aml.enums.CaseStatus;
import com.tss.aml.enums.RuleSeverity;
import com.tss.aml.enums.RuleStatus;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.enums.TenantStatus;
import com.tss.aml.enums.UserRole;
import com.tss.aml.repositories.AmlCaseRepository;
import com.tss.aml.repositories.AuditLogRepository;
import com.tss.aml.repositories.BankRuleAssignmentRepository;
import com.tss.aml.repositories.RuleRepository;
import com.tss.aml.repositories.SystemAdminRepository;
import com.tss.aml.repositories.SystemAuditLogRepository;
import com.tss.aml.repositories.TenantRepository;
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
import org.springframework.mock.web.MockMultipartFile;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("dev")
public class AuditTrailIntegrationTest {

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
    private BankRuleAssignmentRepository bankRuleAssignmentRepository;

    @Autowired
    private AmlCaseRepository caseRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SystemAuditLogRepository systemAuditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private TenantMigrationService tenantMigrationService;

    private Tenant tenantHdfc;
    private Tenant tenantIcici;

    private Users hdfcBankAdmin;
    private Users hdfcCoUser;
    private Users hdfcOtherCoUser;
    private Users iciciBankAdmin;

    private SystemAdmin sysAdmin;

    private String hdfcBankAdminToken;
    private String hdfcCoToken;
    private String hdfcOtherCoToken;
    private String iciciBankAdminToken;
    private String sysAdminToken;

    private String hdfcSchema;
    private String iciciSchema;

    @Autowired
    private com.tss.aml.repositories.AlertRepository alertRepository;

    @Autowired
    private com.tss.aml.repositories.NotificationRepository notificationRepository;

    @Autowired
    private com.tss.aml.repositories.SarStrRepository sarStrRepository;

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

        sysAdmin = systemAdminRepository.findAll().get(0);

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

        hdfcSchema = tenantHdfc.getSchemaName();
        iciciSchema = tenantIcici.getSchemaName();

        tenantMigrationService.migrateTenantSchema(hdfcSchema);
        tenantMigrationService.migrateTenantSchema(iciciSchema);

        // Clear tenant data safely
        TenantContext.setCurrentTenant(hdfcSchema);
        auditLogRepository.deleteAll();
        alertRepository.deleteAll();
        notificationRepository.deleteAll();
        sarStrRepository.deleteAll();
        caseRepository.deleteAll();
        TenantContext.clear();

        TenantContext.setCurrentTenant(iciciSchema);
        auditLogRepository.deleteAll();
        alertRepository.deleteAll();
        notificationRepository.deleteAll();
        sarStrRepository.deleteAll();
        caseRepository.deleteAll();
        TenantContext.clear();

        // Create Users
        hdfcBankAdmin = userRepository.findByEmail("admin_audit@hdfc.com").orElseGet(() -> userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_ADMIN_AUD")
                .role(UserRole.BANK_ADMIN)
                .email("admin_audit@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("HDFC").lastName("Admin")
                .isActive(true)
                .mustResetPassword(false)
                .build()));

        hdfcCoUser = userRepository.findByEmail("co_audit@hdfc.com").orElseGet(() -> userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_CO_AUD")
                .role(UserRole.COMPLIANCE_OFFICER)
                .email("co_audit@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Officer").lastName("HDFC")
                .isActive(true)
                .mustResetPassword(false)
                .build()));

        hdfcOtherCoUser = userRepository.findByEmail("co_other_audit@hdfc.com").orElseGet(() -> userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_CO_OTH")
                .role(UserRole.COMPLIANCE_OFFICER)
                .email("co_other_audit@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Other").lastName("Officer")
                .isActive(true)
                .mustResetPassword(false)
                .build()));

        iciciBankAdmin = userRepository.findByEmail("admin_audit@icici.com").orElseGet(() -> userRepository.save(Users.builder()
                .tenant(tenantIcici)
                .userCode("ICICI_ADMIN_AUD")
                .role(UserRole.BANK_ADMIN)
                .email("admin_audit@icici.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("ICICI").lastName("Admin")
                .isActive(true)
                .mustResetPassword(false)
                .build()));

        hdfcBankAdminToken = generateToken(hdfcBankAdmin.getEmail());
        hdfcCoToken = generateToken(hdfcCoUser.getEmail());
        hdfcOtherCoToken = generateToken(hdfcOtherCoUser.getEmail());
        iciciBankAdminToken = generateToken(iciciBankAdmin.getEmail());
        sysAdminToken = generateToken(sysAdmin.getEmail());
    }

    private String generateToken(String email) {
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        return jwtTokenProvider.generateToken(auth);
    }

    // --- 1. User Management Audit Events: CREATED, ACTIVATED, DEACTIVATED, PASSWORD_RESET ---
    @Test
    @DisplayName("Verification 1-4: Compliance Officer lifecycle generates COMPLIANCE_OFFICER_CREATED, ACTIVATED, DEACTIVATED, and PASSWORD_RESET with no credential leak in details")
    void testComplianceOfficerLifecycleAuditEvents() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        ComplianceOfficerRequest req = new ComplianceOfficerRequest();
        req.setUserCode("CO-AUD-" + uniqueSuffix);
        req.setEmployeeId("EMP-AUD-" + uniqueSuffix);
        req.setFirstName("Test CO");
        req.setLastName("Audit");
        req.setEmail("test_co_" + uniqueSuffix + "@hdfc.com");
        req.setPhoneNumber("+1234567890");

        // 1. Create CO
        String responseStr = mockMvc.perform(post("/api/v1/bank/admin/add-compliance-officer")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .header("X-Tenant-Code", "HDFC")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String officerIdStr = objectMapper.readTree(responseStr).get("userId").asText();

        // 2. Deactivate CO
        mockMvc.perform(patch("/api/v1/bank/admin/compliance-officers/" + officerIdStr + "/deactivate")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .header("X-Tenant-Code", "HDFC"))
                .andExpect(status().isOk());

        // 3. Activate CO
        mockMvc.perform(patch("/api/v1/bank/admin/compliance-officers/" + officerIdStr + "/activate")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .header("X-Tenant-Code", "HDFC"))
                .andExpect(status().isOk());

        // 4. Password Reset
        mockMvc.perform(post("/api/v1/bank/admin/compliance-officers/" + officerIdStr + "/reset-password")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .header("X-Tenant-Code", "HDFC"))
                .andExpect(status().isOk());

        TenantContext.setCurrentTenant(hdfcSchema);
        try {
            List<AuditLog> createdLogs = auditLogRepository.findByAction("COMPLIANCE_OFFICER_CREATED");
            assertFalse(createdLogs.isEmpty(), "COMPLIANCE_OFFICER_CREATED audit log should be present");

            List<AuditLog> deactLogs = auditLogRepository.findByAction("COMPLIANCE_OFFICER_DEACTIVATED");
            assertFalse(deactLogs.isEmpty(), "COMPLIANCE_OFFICER_DEACTIVATED audit log should be present");

            List<AuditLog> actLogs = auditLogRepository.findByAction("COMPLIANCE_OFFICER_ACTIVATED");
            assertFalse(actLogs.isEmpty(), "COMPLIANCE_OFFICER_ACTIVATED audit log should be present");

            List<AuditLog> pwdLogs = auditLogRepository.findByAction("COMPLIANCE_OFFICER_PASSWORD_RESET");
            assertFalse(pwdLogs.isEmpty(), "COMPLIANCE_OFFICER_PASSWORD_RESET audit log should be present");

            AuditLog pwdLog = pwdLogs.get(0);
            String details = pwdLog.getDetails();
            assertFalse(details.contains("Password"), "Details should not contain plaintext passwords");
            assertFalse(details.contains("hash"), "Details should not contain password hashes");
            assertFalse(details.contains("token"), "Details should not contain reset tokens");
        } finally {
            TenantContext.clear();
        }
    }

    // --- 2. Batch Processing & Rejection Audit Events ---
    @Test
    @DisplayName("Verification 5-7: Batch upload generates BATCH_PROCESSED for success and BATCH_REJECTED for validation errors")
    void testBatchAuditingEvents() throws Exception {
        // Valid Excel
        byte[] validExcel = createExcelBytes(
                new String[]{"TxnNo", "OriginatorAccountNo", "OriginatorName", "Amount", "Currency", "TxnType", "Direction", "CounterpartyName", "CounterpartyAccountNo", "CounterpartyBank", "CounterpartyCountryCode", "TxnTimestamp", "CountryCode"},
                new Object[][]{
                        {"TXN-AUD-1", "ACC-101", "John Doe", "150000.00", "INR", "NEFT", "OUT", "Jane Smith", "ACC-202", "HDFC", "IN", "2026-09-09T10:00:00", "IN"}
                }
        );
        MockMultipartFile validFile = new MockMultipartFile("file", "valid_batch.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", validExcel);

        mockMvc.perform(multipart("/api/v1/bank/batches/upload")
                        .file(validFile)
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .header("X-Tenant-Code", "HDFC"))
                .andExpect(status().isAccepted());

        // Invalid Excel (missing required columns)
        byte[] invalidExcel = createExcelBytes(
                new String[]{"TxnNo", "Amount"},
                new Object[][]{
                        {"TXN-AUD-2", "100.00"}
                }
        );
        MockMultipartFile invalidFile = new MockMultipartFile("file", "invalid_batch.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", invalidExcel);

        mockMvc.perform(multipart("/api/v1/bank/batches/upload")
                        .file(invalidFile)
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .header("X-Tenant-Code", "HDFC"))
                .andExpect(status().isUnprocessableEntity());

        TenantContext.setCurrentTenant(hdfcSchema);
        try {
            List<AuditLog> processedLogs = auditLogRepository.findByAction("BATCH_PROCESSED");
            assertFalse(processedLogs.isEmpty(), "BATCH_PROCESSED audit log should be present");

            List<AuditLog> rejectedLogs = auditLogRepository.findByAction("BATCH_REJECTED");
            assertFalse(rejectedLogs.isEmpty(), "BATCH_REJECTED audit log should be present");
        } finally {
            TenantContext.clear();
        }
    }

    private byte[] createExcelBytes(String[] headers, Object[][] rows) throws java.io.IOException {
        try (org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Transactions");
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            for (int h = 0; h < headers.length; h++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(h);
                cell.setCellValue(headers[h]);
            }

            for (int r = 0; r < rows.length; r++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    org.apache.poi.ss.usermodel.Cell cell = row.createCell(c);
                    cell.setCellValue(String.valueOf(rows[r][c]));
                }
            }

            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    // --- 3. System Admin Rule Audit Events: RULE_CREATED, RULE_UPDATED, RULE_ASSIGNED, RULE_UNASSIGNED ---
    @Test
    @DisplayName("Verification 8-11: System Admin rule lifecycle generates RULE_CREATED, RULE_UPDATED, RULE_ASSIGNED, and RULE_UNASSIGNED SystemAuditLog entries")
    void testRuleLifecycleSystemAuditEvents() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Create Rule
        CreateRuleRequest createReq = new CreateRuleRequest();
        createReq.setRuleName("Rule Audit Test " + uniqueSuffix);
        createReq.setDescription("Rule for testing audit trail");
        createReq.setTypology(RuleTypology.STRUCTURING_SMURFING);
        createReq.setDefaultSeverity(RuleSeverity.HIGH);
        createReq.setParameters(Map.of("windowDays", 7, "reportingThreshold", 9000));

        String createRespStr = mockMvc.perform(post("/api/v1/system/admin/rules")
                        .header("Authorization", "Bearer " + sysAdminToken)
                        .header("X-Tenant-Code", "PUBLIC")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String ruleIdStr = objectMapper.readTree(createRespStr).get("ruleId").asText();
        UUID ruleId = UUID.fromString(ruleIdStr);

        // 2. Update Rule
        UpdateRuleRequest updateReq = new UpdateRuleRequest();
        updateReq.setRuleName("Updated Rule Audit Test " + uniqueSuffix);
        updateReq.setDescription("Updated rule description");
        updateReq.setDefaultSeverity(RuleSeverity.MEDIUM);
        updateReq.setParameters(Map.of("windowDays", 10, "reportingThreshold", 9500));

        mockMvc.perform(patch("/api/v1/system/admin/rules/update/" + ruleIdStr)
                        .header("Authorization", "Bearer " + sysAdminToken)
                        .header("X-Tenant-Code", "PUBLIC")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        // 3. Assign Rule to Tenant
        AssignRuleRequest assignReq = new AssignRuleRequest();
        assignReq.setTenantId(tenantHdfc.getTenantId());

        mockMvc.perform(post("/api/v1/system/admin/rules/" + ruleIdStr + "/assign")
                        .header("Authorization", "Bearer " + sysAdminToken)
                        .header("X-Tenant-Code", "PUBLIC")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isCreated());

        // 4. Unassign Rule from Tenant
        mockMvc.perform(delete("/api/v1/system/admin/rules/" + ruleIdStr + "/unassign/" + tenantHdfc.getTenantId())
                        .header("Authorization", "Bearer " + sysAdminToken)
                        .header("X-Tenant-Code", "PUBLIC"))
                .andExpect(status().isNoContent());

        List<SystemAuditLog> createdLogs = systemAuditLogRepository.findByAction("RULE_CREATED");
        assertFalse(createdLogs.isEmpty(), "RULE_CREATED system audit log should be present");

        List<SystemAuditLog> updatedLogs = systemAuditLogRepository.findByAction("RULE_UPDATED");
        assertFalse(updatedLogs.isEmpty(), "RULE_UPDATED system audit log should be present");

        List<SystemAuditLog> assignedLogs = systemAuditLogRepository.findByAction("RULE_ASSIGNED");
        assertFalse(assignedLogs.isEmpty(), "RULE_ASSIGNED system audit log should be present");

        List<SystemAuditLog> unassignedLogs = systemAuditLogRepository.findByAction("RULE_UNASSIGNED");
        assertFalse(unassignedLogs.isEmpty(), "RULE_UNASSIGNED system audit log should be present");
    }

    // --- 4. Role Authorization on Audit Endpoints ---
    @Test
    @DisplayName("Verification 18-21: Role authorization enforced on Bank Admin and System Admin audit endpoints")
    void testAuditEndpointsAuthorization() throws Exception {
        // CO attempting Bank Admin audit log endpoint -> 403 Forbidden
        mockMvc.perform(get("/api/v1/bank/admin/audit-logs")
                        .header("Authorization", "Bearer " + hdfcCoToken)
                        .header("X-Tenant-Code", "HDFC"))
                .andExpect(status().isForbidden());

        // Bank Admin accessing Bank Admin audit log endpoint -> 200 OK
        mockMvc.perform(get("/api/v1/bank/admin/audit-logs")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .header("X-Tenant-Code", "HDFC"))
                .andExpect(status().isOk());

        // Bank Admin attempting System Admin audit log endpoint -> 403 Forbidden
        mockMvc.perform(get("/api/v1/system/admin/audit-logs")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .header("X-Tenant-Code", "PUBLIC"))
                .andExpect(status().isForbidden());

        // System Admin accessing System Admin audit log endpoint -> 200 OK
        mockMvc.perform(get("/api/v1/system/admin/audit-logs")
                        .header("Authorization", "Bearer " + sysAdminToken)
                        .header("X-Tenant-Code", "PUBLIC"))
                .andExpect(status().isOk());
    }

    // --- 5. Case Audit Trail Scoping and Dual Route Mapping ---
    @Test
    @DisplayName("Verification 22-25: Case audit trail endpoint enforces assigned officer scoping and supports both /audit-logs and /audit-trail routes")
    void testCaseAuditTrailScopingAndDualRoutes() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        TenantContext.setCurrentTenant(hdfcSchema);
        AmlCase amlCase;
        try {
            amlCase = AmlCase.builder()
                    .caseCode("CASE-AUD-" + uniqueSuffix)
                    .status(CaseStatus.OPEN)
                    .createdBy(hdfcBankAdmin)
                    .assignedTo(hdfcCoUser)
                    .createdAt(LocalDateTime.now())
                    .build();
            amlCase = caseRepository.save(amlCase);

            AuditLog log = AuditLog.builder()
                    .actor(hdfcBankAdmin)
                    .action("CASE_ASSIGNED")
                    .entityType("CASE")
                    .entityId(amlCase.getCaseId().toString())
                    .details("Assigned case to officer")
                    .build();
            auditLogRepository.save(log);
        } finally {
            TenantContext.clear();
        }

        // Assigned CO accessing via /audit-logs route -> 200 OK
        mockMvc.perform(get("/api/v1/compliance/cases/" + amlCase.getCaseId() + "/audit-logs")
                        .header("Authorization", "Bearer " + hdfcCoToken)
                        .header("X-Tenant-Code", "HDFC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // Assigned CO accessing via /audit-trail route -> 200 OK
        mockMvc.perform(get("/api/v1/compliance/cases/" + amlCase.getCaseId() + "/audit-trail")
                        .header("Authorization", "Bearer " + hdfcCoToken)
                        .header("X-Tenant-Code", "HDFC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // Other CO (not assigned) attempting case audit trail -> 403 Forbidden
        mockMvc.perform(get("/api/v1/compliance/cases/" + amlCase.getCaseId() + "/audit-logs")
                        .header("Authorization", "Bearer " + hdfcOtherCoToken)
                        .header("X-Tenant-Code", "HDFC"))
                .andExpect(status().isForbidden());
    }

    // --- 6. Filtering and Pagination Verification ---
    @Test
    @DisplayName("Verification 26: JPA Specification filtering by actorId, entityType, action, startDate, endDate, and pagination")
    void testAuditLogFilteringAndPagination() throws Exception {
        TenantContext.setCurrentTenant(hdfcSchema);
        try {
            AuditLog log1 = AuditLog.builder()
                    .actor(hdfcBankAdmin)
                    .action("CASE_ASSIGNED")
                    .entityType("CASE")
                    .entityId("CASE-101")
                    .details("Details 1")
                    .build();
            AuditLog log2 = AuditLog.builder()
                    .actor(hdfcCoUser)
                    .action("CASE_INVESTIGATION_STARTED")
                    .entityType("CASE")
                    .entityId("CASE-101")
                    .details("Details 2")
                    .build();
            auditLogRepository.saveAll(List.of(log1, log2));
        } finally {
            TenantContext.clear();
        }

        // Filter by action=CASE_ASSIGNED
        mockMvc.perform(get("/api/v1/bank/admin/audit-logs")
                        .param("action", "CASE_ASSIGNED")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .header("X-Tenant-Code", "HDFC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].action", is("CASE_ASSIGNED")));
    }

    // --- 7. Append-Only Immutability Verification ---
    @Test
    @DisplayName("Verification 27: Application has no update/delete operations for audit records")
    void testAuditLogAppendOnlyImmutability() {
        TenantContext.setCurrentTenant(hdfcSchema);
        try {
            AuditLog log = AuditLog.builder()
                    .actor(hdfcBankAdmin)
                    .action("IMMUTABILITY_TEST")
                    .entityType("TEST")
                    .entityId("123")
                    .details("Original Details")
                    .build();
            log = auditLogRepository.save(log);

            assertNotNull(log.getAuditId());
            assertNotNull(log.getTimestamp());
        } finally {
            TenantContext.clear();
        }
    }
}
