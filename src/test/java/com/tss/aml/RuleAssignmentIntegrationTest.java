package com.tss.aml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.aml.dtos.batch.BatchUploadResponseDto;
import com.tss.aml.dtos.rule.AssignRuleRequest;
import com.tss.aml.entities.system.BankRuleAssignment;
import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.entities.system.Tenant;
import com.tss.aml.entities.system.Users;
import com.tss.aml.enums.BatchStatus;
import com.tss.aml.enums.RuleSeverity;
import com.tss.aml.enums.RuleStatus;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.enums.TenantStatus;
import com.tss.aml.enums.UserRole;
import com.tss.aml.repositories.AlertRepository;
import com.tss.aml.repositories.BankRuleAssignmentRepository;
import com.tss.aml.repositories.BatchValidationErrorRepository;
import com.tss.aml.repositories.FinancialTransactionRepository;
import com.tss.aml.repositories.RuleRepository;
import com.tss.aml.repositories.SystemAdminRepository;
import com.tss.aml.repositories.TenantRepository;
import com.tss.aml.repositories.TransactionBatchRepository;
import com.tss.aml.repositories.UserRepository;
import com.tss.aml.security.CustomUserDetails;
import com.tss.aml.security.CustomUserDetailsService;
import com.tss.aml.security.JwtTokenProvider;
import com.tss.aml.services.interfaces.BatchIngestionService;
import com.tss.aml.tenant.TenantContext;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class RuleAssignmentIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SystemAdminRepository systemAdminRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private BankRuleAssignmentRepository bankRuleAssignmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private BatchIngestionService batchIngestionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private JavaMailSender mailSender;

    private MockMvc mockMvc;
    private SystemAdmin systemAdmin;
    private String sysAdminJwtToken;

    private Tenant activeTenantA;
    private Tenant activeTenantB;
    private Tenant inactiveTenant;

    private Rule activeRule1;
    private Rule activeRule2;
    private Rule draftRule;

    private Users tenantAUser;
    private Users tenantBUser;
    private String tenantAJwtToken;

    @Autowired
    private TransactionBatchRepository batchRepository;

    @Autowired
    private FinancialTransactionRepository transactionRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private BatchValidationErrorRepository batchValidationErrorRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        TenantContext.clear();
        SecurityContextHolder.clearContext();

        try {
            java.util.List<String> schemas = jdbcTemplate.queryForList("SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%'", String.class);
            schemas.add("public");
            java.util.List<String> tables = java.util.List.of("aml_case", "alert", "financial_transaction", "batch_validation_error", "transaction_batch", "account");
            for (String schema : schemas) {
                for (String table : tables) {
                    try {
                        jdbcTemplate.execute("TRUNCATE TABLE " + schema + "." + table + " CASCADE");
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}

        alertRepository.deleteAll();
        transactionRepository.deleteAll();
        batchValidationErrorRepository.deleteAll();
        batchRepository.deleteAll();
        bankRuleAssignmentRepository.deleteAll();
        userRepository.deleteAll();
        ruleRepository.deleteAll();
        tenantRepository.deleteAll();

        systemAdmin = systemAdminRepository.findByEmail("admin@aml.com")
                .orElseGet(() -> {
                    SystemAdmin admin = new SystemAdmin();
                    admin.setSystemAdminCode("SYSADMIN001");
                    admin.setFirstName("System");
                    admin.setLastName("Admin");
                    admin.setEmail("admin@aml.com");
                    admin.setPhoneNumber("1234567890");
                    admin.setPasswordHash(passwordEncoder.encode("Admin@123"));
                    admin.setIsActive(true);
                    return systemAdminRepository.save(admin);
                });

        UserDetails adminDetails = customUserDetailsService.loadUserByUsername(systemAdmin.getEmail());
        Authentication sysAuth = new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(sysAuth);
        sysAdminJwtToken = jwtTokenProvider.generateToken(sysAuth);

        activeTenantA = tenantRepository.save(Tenant.builder()
                .tenantCode("HDFC_RULE")
                .tenantName("HDFC Rule Bank")
                .displayName("HDFC Rule Bank")
                .schemaName("tenant_hdfc_rule")
                .status(TenantStatus.ACTIVE)
                .onboardedByAdmin(systemAdmin)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        activeTenantB = tenantRepository.save(Tenant.builder()
                .tenantCode("ICICI_RULE")
                .tenantName("ICICI Rule Bank")
                .displayName("ICICI Rule Bank")
                .schemaName("tenant_icici_rule")
                .status(TenantStatus.ACTIVE)
                .onboardedByAdmin(systemAdmin)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        inactiveTenant = tenantRepository.save(Tenant.builder()
                .tenantCode("INACTIVE_BANK")
                .tenantName("Inactive Bank")
                .displayName("Inactive Bank")
                .schemaName("tenant_inactive")
                .status(TenantStatus.ONBOARDING)
                .onboardedByAdmin(systemAdmin)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        activeRule1 = ruleRepository.save(Rule.builder()
                .ruleCode("RULE-GEO-001")
                .ruleName("Geographic Risk Rule")
                .description("High risk country check")
                .typology(RuleTypology.GEOGRAPHIC_RISK)
                .defaultSeverity(RuleSeverity.HIGH)
                .status(RuleStatus.ACTIVE)
                .parameters(Map.of("highRiskCountries", List.of("IR", "KP"), "minAmount", 50000))
                .build());

        activeRule2 = ruleRepository.save(Rule.builder()
                .ruleCode("RULE-RAPID-001")
                .ruleName("Rapid Movement Rule")
                .description("Rapid movement of funds")
                .typology(RuleTypology.STRUCTURING_SMURFING)
                .defaultSeverity(RuleSeverity.MEDIUM)
                .status(RuleStatus.ACTIVE)
                .parameters(Map.of("windowDays", 7, "minCount", 3))
                .build());

        draftRule = ruleRepository.save(Rule.builder()
                .ruleCode("RULE-DRAFT-001")
                .ruleName("Draft Rule")
                .description("Draft rule under review")
                .typology(RuleTypology.STRUCTURING_SMURFING)
                .defaultSeverity(RuleSeverity.LOW)
                .status(RuleStatus.DRAFT)
                .parameters(Map.of("thresholdAmount", 1000000))
                .build());

        tenantAUser = userRepository.save(Users.builder()
                .tenant(activeTenantA)
                .userCode("HDFC_ADMIN_RULE")
                .role(UserRole.BANK_ADMIN)
                .firstName("HDFC")
                .lastName("Admin")
                .email("admin@hdfcrule.com")
                .passwordHash(passwordEncoder.encode("Password@123"))
                .isActive(true)
                .mustResetPassword(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        tenantBUser = userRepository.save(Users.builder()
                .tenant(activeTenantB)
                .userCode("ICICI_ADMIN_RULE")
                .role(UserRole.BANK_ADMIN)
                .firstName("ICICI")
                .lastName("Admin")
                .email("admin@icicirule.com")
                .passwordHash(passwordEncoder.encode("Password@123"))
                .isActive(true)
                .mustResetPassword(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        CustomUserDetails tenantUserDetails = CustomUserDetails.builder()
                .userId(tenantAUser.getUserId())
                .username(tenantAUser.getEmail())
                .password(tenantAUser.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_BANK_ADMIN")))
                .tenantId(activeTenantA.getTenantId())
                .tenantCode(activeTenantA.getTenantCode())
                .enabled(true)
                .accountNonLocked(true)
                .mustResetPassword(false)
                .build();

        Authentication tenantAuth = new UsernamePasswordAuthenticationToken(tenantUserDetails, null, tenantUserDetails.getAuthorities());
        tenantAJwtToken = jwtTokenProvider.generateToken(tenantAuth);
    }

    @Test
    @DisplayName("1. System Admin can assign active rule to active tenant with server-side metadata")
    void systemAdminCanAssignActiveRuleToActiveTenant() throws Exception {
        AssignRuleRequest request = AssignRuleRequest.builder()
                .tenantId(activeTenantA.getTenantId())
                .build();

        mockMvc.perform(post("/api/v1/system/admin/rules/{ruleId}/assign", activeRule1.getRuleId())
                        .header("Authorization", "Bearer " + sysAdminJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignmentId").exists())
                .andExpect(jsonPath("$.tenantId").value(activeTenantA.getTenantId().toString()))
                .andExpect(jsonPath("$.tenantCode").value("hdfc_rule"))
                .andExpect(jsonPath("$.ruleId").value(activeRule1.getRuleId().toString()))
                .andExpect(jsonPath("$.ruleCode").value("RULE-GEO-001"))
                .andExpect(jsonPath("$.assignedByAdminId").value(systemAdmin.getSystemAdminId().toString()))
                .andExpect(jsonPath("$.assignedByEmail").value(systemAdmin.getEmail()))
                .andExpect(jsonPath("$.assignedAt").exists());

        assertThat(bankRuleAssignmentRepository.existsByTenant_TenantIdAndRule_RuleId(
                activeTenantA.getTenantId(), activeRule1.getRuleId()
        )).isTrue();
    }

    @Test
    @DisplayName("2. Non-System Admin receives 403 Forbidden when attempting rule assignment")
    void nonSystemAdminForbiddenFromAssigningRule() throws Exception {
        AssignRuleRequest request = AssignRuleRequest.builder()
                .tenantId(activeTenantA.getTenantId())
                .build();

        mockMvc.perform(post("/api/v1/system/admin/rules/{ruleId}/assign", activeRule1.getRuleId())
                        .header("Authorization", "Bearer " + tenantAJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("3. Rule assignment fails for nonexistent tenant")
    void assignRuleToNonexistentTenantFails() throws Exception {
        AssignRuleRequest request = AssignRuleRequest.builder()
                .tenantId(UUID.randomUUID())
                .build();

        mockMvc.perform(post("/api/v1/system/admin/rules/{ruleId}/assign", activeRule1.getRuleId())
                        .header("Authorization", "Bearer " + sysAdminJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Tenant not found: " + request.getTenantId()));
    }

    @Test
    @DisplayName("4. Rule assignment fails for inactive tenant")
    void assignRuleToInactiveTenantFails() throws Exception {
        AssignRuleRequest request = AssignRuleRequest.builder()
                .tenantId(inactiveTenant.getTenantId())
                .build();

        mockMvc.perform(post("/api/v1/system/admin/rules/{ruleId}/assign", activeRule1.getRuleId())
                        .header("Authorization", "Bearer " + sysAdminJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Tenant is not active: " + inactiveTenant.getTenantId()));
    }

    @Test
    @DisplayName("5. Rule assignment fails for nonexistent rule")
    void assignNonexistentRuleFails() throws Exception {
        UUID nonexistentRuleId = UUID.randomUUID();
        AssignRuleRequest request = AssignRuleRequest.builder()
                .tenantId(activeTenantA.getTenantId())
                .build();

        mockMvc.perform(post("/api/v1/system/admin/rules/{ruleId}/assign", nonexistentRuleId)
                        .header("Authorization", "Bearer " + sysAdminJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Rule not found: " + nonexistentRuleId));
    }

    @Test
    @DisplayName("6. Rule assignment fails for DRAFT rule")
    void assignDraftRuleFails() throws Exception {
        AssignRuleRequest request = AssignRuleRequest.builder()
                .tenantId(activeTenantA.getTenantId())
                .build();

        mockMvc.perform(post("/api/v1/system/admin/rules/{ruleId}/assign", draftRule.getRuleId())
                        .header("Authorization", "Bearer " + sysAdminJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Rule is not active: " + draftRule.getRuleId()));
    }

    @Test
    @DisplayName("7. Duplicate rule assignment to same tenant is rejected")
    void duplicateRuleAssignmentFails() throws Exception {
        bankRuleAssignmentRepository.save(BankRuleAssignment.builder()
                .tenant(activeTenantA)
                .rule(activeRule1)
                .assignedBy(systemAdmin)
                .assignedAt(LocalDateTime.now())
                .build());

        AssignRuleRequest request = AssignRuleRequest.builder()
                .tenantId(activeTenantA.getTenantId())
                .build();

        mockMvc.perform(post("/api/v1/system/admin/rules/{ruleId}/assign", activeRule1.getRuleId())
                        .header("Authorization", "Bearer " + sysAdminJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Rule is already assigned to tenant: " + activeTenantA.getTenantId()));
    }

    @Test
    @DisplayName("8. Retrieve assigned rules for a tenant succeeds")
    void getAssignedRulesForTenantSuccess() throws Exception {
        bankRuleAssignmentRepository.save(BankRuleAssignment.builder()
                .tenant(activeTenantA)
                .rule(activeRule1)
                .assignedBy(systemAdmin)
                .assignedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/v1/system/admin/rules/tenants/{tenantId}", activeTenantA.getTenantId())
                        .header("Authorization", "Bearer " + sysAdminJwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ruleCode").value("RULE-GEO-001"));
    }

    @Test
    @DisplayName("9. Unassign rule from tenant succeeds")
    void unassignRuleFromTenantSuccess() throws Exception {
        bankRuleAssignmentRepository.save(BankRuleAssignment.builder()
                .tenant(activeTenantA)
                .rule(activeRule1)
                .assignedBy(systemAdmin)
                .assignedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(delete("/api/v1/system/admin/rules/{ruleId}/assign/{tenantId}",
                        activeRule1.getRuleId(), activeTenantA.getTenantId())
                        .header("Authorization", "Bearer " + sysAdminJwtToken))
                .andExpect(status().isNoContent());

        assertThat(bankRuleAssignmentRepository.existsByTenant_TenantIdAndRule_RuleId(
                activeTenantA.getTenantId(), activeRule1.getRuleId()
        )).isFalse();
    }

    @Test
    @DisplayName("10. Batch processing evaluates only rules assigned to tenant; Bank B and unassigned banks do NOT get global active rules fallback")
    void batchProcessingUsesOnlyAssignedTenantRulesAndNoFallback() throws Exception {
        // Assign activeRule1 to Tenant A (HDFC) only
        bankRuleAssignmentRepository.save(BankRuleAssignment.builder()
                .tenant(activeTenantA)
                .rule(activeRule1)
                .assignedBy(systemAdmin)
                .assignedAt(LocalDateTime.now())
                .build());

        MockMultipartFile excelFile = createValidExcelBatchFile();

        // 1. Process batch for Tenant A (has activeRule1 assigned)
        BatchUploadResponseDto responseA = batchIngestionService.processBatchUpload(excelFile, tenantAUser);
        assertThat(responseA.getStatus()).isEqualTo(BatchStatus.PROCESSED_ALERTS_GENERATED);
        assertThat(responseA.getAlertsGeneratedCount()).isGreaterThan(0);

        // 2. Process batch for Tenant B (has NO rules assigned)
        // Must NOT fallback to activeRule1 or activeRule2 globally!
        BatchUploadResponseDto responseB = batchIngestionService.processBatchUpload(excelFile, tenantBUser);
        assertThat(responseB.getStatus()).isEqualTo(BatchStatus.PROCESSED_NO_ALERTS);
        assertThat(responseB.getAlertsGeneratedCount()).isEqualTo(0);
    }

    private MockMultipartFile createValidExcelBatchFile() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Transactions");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("TxnNo");
            header.createCell(1).setCellValue("OriginatorAccountNo");
            header.createCell(2).setCellValue("OriginatorName");
            header.createCell(3).setCellValue("Amount");
            header.createCell(4).setCellValue("Currency");
            header.createCell(5).setCellValue("TxnType");
            header.createCell(6).setCellValue("Direction");
            header.createCell(7).setCellValue("CounterpartyName");
            header.createCell(8).setCellValue("CounterpartyAccountNo");
            header.createCell(9).setCellValue("CounterpartyBank");
            header.createCell(10).setCellValue("CounterpartyCountryCode");
            header.createCell(11).setCellValue("TxnTimestamp");
            header.createCell(12).setCellValue("CountryCode");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("TXN10001");
            row.createCell(1).setCellValue("ACC-HDFC-001");
            row.createCell(2).setCellValue("John Doe");
            row.createCell(3).setCellValue("60000.0");
            row.createCell(4).setCellValue("INR");
            row.createCell(5).setCellValue("NEFT");
            row.createCell(6).setCellValue("OUT");
            row.createCell(7).setCellValue("Tehran Traders");
            row.createCell(8).setCellValue("ACC-IRN-999");
            row.createCell(9).setCellValue("Bank Markazi");
            row.createCell(10).setCellValue("IR");
            row.createCell(11).setCellValue("2026-09-06T10:00:00");
            row.createCell(12).setCellValue("IN");

            workbook.write(out);

            return new MockMultipartFile(
                    "file",
                    "batch.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray()
            );
        }
    }
}
