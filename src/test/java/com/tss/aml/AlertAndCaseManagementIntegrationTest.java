package com.tss.aml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.aml.config.SystemAdminDataInitializer;
import com.tss.aml.dtos.tenant.ComplianceOfficerRequest;
import com.tss.aml.dtos.tenant.CreateBankAdminRequest;
import com.tss.aml.dtos.tenant.CreateCaseRequest;
import com.tss.aml.dtos.tenant.ReassignCaseRequest;
import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.entities.system.Tenant;
import com.tss.aml.entities.system.Users;
import com.tss.aml.entities.tenant.Account;
import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.entities.tenant.AuditLog;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.entities.tenant.Notification;
import com.tss.aml.entities.tenant.TransactionBatch;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.AlertStatus;
import com.tss.aml.enums.BatchStatus;
import com.tss.aml.enums.RuleSeverity;
import com.tss.aml.enums.RuleStatus;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.enums.TenantStatus;
import com.tss.aml.enums.TransactionDirection;
import com.tss.aml.enums.TransactionType;
import com.tss.aml.repositories.AccountRepository;
import com.tss.aml.repositories.AlertRepository;
import com.tss.aml.repositories.AmlCaseRepository;
import com.tss.aml.repositories.AuditLogRepository;
import com.tss.aml.repositories.FinancialTransactionRepository;
import com.tss.aml.repositories.NotificationRepository;
import com.tss.aml.repositories.RuleRepository;
import com.tss.aml.repositories.SystemAdminRepository;
import com.tss.aml.repositories.TenantRepository;
import com.tss.aml.repositories.TransactionBatchRepository;
import com.tss.aml.repositories.UserRepository;
import com.tss.aml.security.CustomUserDetailsService;
import com.tss.aml.security.JwtTokenProvider;
import com.tss.aml.tenant.TenantContext;
import com.tss.aml.services.TenantMigrationService;
import com.tss.aml.tenant.TenantService;
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
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AlertAndCaseManagementIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SystemAdminDataInitializer initializer;

    @Autowired
    private SystemAdminRepository systemAdminRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TenantMigrationService tenantMigrationService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AmlCaseRepository amlCaseRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionBatchRepository transactionBatchRepository;

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private SystemAdmin systemAdmin;
    private Tenant testTenant;
    private Users bankAdminUser;
    private String bankAdminToken;
    private Rule highRule;
    private Rule medRule;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        TenantContext.clear();
        SecurityContextHolder.clearContext();

        try {
            TenantContext.setCurrentTenant("tenant_alert_mgmt");
            alertRepository.deleteAll();
            amlCaseRepository.deleteAll();
            financialTransactionRepository.deleteAll();
            transactionBatchRepository.deleteAll();
            accountRepository.deleteAll();
            auditLogRepository.deleteAll();
            notificationRepository.deleteAll();
        } catch (Exception ignored) {
        } finally {
            TenantContext.clear();
        }

        userRepository.findByEmail("co_alert_1@bank.com").ifPresent(userRepository::delete);
        userRepository.findByUserCode("CO_ALERT_01").ifPresent(userRepository::delete);
        userRepository.findByEmail("co_alert_2@bank.com").ifPresent(userRepository::delete);
        userRepository.findByUserCode("CO_ALERT_02").ifPresent(userRepository::delete);
        userRepository.findByEmail("admin_alert@bank.com").ifPresent(userRepository::delete);
        userRepository.findByUserCode("ADMIN_ALERT_01").ifPresent(userRepository::delete);

        if (systemAdminRepository.count() == 0) {
            initializer.run(null);
        }
        systemAdmin = systemAdminRepository.findAll().get(0);

        testTenant = tenantRepository.findByTenantCode("ALERT_BANK")
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .tenantCode("ALERT_BANK")
                        .tenantName("Alert Test Bank")
                        .displayName("Alert Bank")
                        .schemaName("tenant_alert_mgmt")
                        .status(TenantStatus.ACTIVE)
                        .onboardedByAdmin(systemAdmin)
                        .build()));

        tenantMigrationService.migrateTenantSchema(testTenant.getSchemaName());

        CreateBankAdminRequest adminReq = new CreateBankAdminRequest();
        adminReq.setUserCode("ADMIN_ALERT_01");
        adminReq.setFirstName("Alert");
        adminReq.setLastName("Admin");
        adminReq.setEmail("admin_alert@bank.com");

        UserDetails adminDetails = customUserDetailsService.loadUserByUsername(systemAdmin.getEmail());
        Authentication sysAuth = new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(sysAuth);

        tenantService.createBankAdmin(testTenant.getTenantId(), adminReq);
        SecurityContextHolder.clearContext();

        bankAdminUser = userRepository.findByEmail("admin_alert@bank.com").orElseThrow();
        bankAdminUser.setMustResetPassword(false);
        userRepository.save(bankAdminUser);

        UserDetails bankAdminDetails = customUserDetailsService.loadUserByUsername("admin_alert@bank.com");
        Authentication bankAuth = new UsernamePasswordAuthenticationToken(bankAdminDetails, null, bankAdminDetails.getAuthorities());
        bankAdminToken = jwtTokenProvider.generateToken(bankAuth);

        // Rules setup
        highRule = ruleRepository.findAll().stream()
                .filter(r -> "RULE_HIGH_01".equals(r.getRuleCode()))
                .findFirst()
                .orElseGet(() -> ruleRepository.save(
                        Rule.builder()
                                .ruleCode("RULE_HIGH_01")
                                .ruleName("High Structuring Flag")
                                .description("Detects high frequency cash deposits under reporting limit")
                                .typology(RuleTypology.STRUCTURING_SMURFING)
                                .parameters(Map.of("threshold", 9000, "count", 3))
                                .defaultSeverity(RuleSeverity.HIGH)
                                .status(RuleStatus.ACTIVE)
                                .build()
                ));

        medRule = ruleRepository.findAll().stream()
                .filter(r -> "RULE_MED_01".equals(r.getRuleCode()))
                .findFirst()
                .orElseGet(() -> ruleRepository.save(
                        Rule.builder()
                                .ruleCode("RULE_MED_01")
                                .ruleName("Medium Rapid Flow")
                                .description("Rapid movement of funds across accounts")
                                .typology(RuleTypology.ROUND_AMOUNT_FLAGGING)
                                .parameters(Map.of("timeWindowMinutes", 60))
                                .defaultSeverity(RuleSeverity.MEDIUM)
                                .status(RuleStatus.ACTIVE)
                                .build()
                ));
    }

    @Test
    @DisplayName("BankAdmin can view Alert Dashboard, filter alerts, and fetch alert stats")
    void alertDashboardAndStats() throws Exception {
        TenantContext.setCurrentTenant(testTenant.getSchemaName());

        Account acc = accountRepository.save(Account.builder()
                .accountNumber("ACC_1001")
                .accountHolderName("John Doe Test")
                .accountType(com.tss.aml.enums.AccountType.SAVINGS)
                .countryCode("IN")
                .openedAt(LocalDateTime.now())
                .build());

        TransactionBatch batch = transactionBatchRepository.save(TransactionBatch.builder()
                .batchCode("BATCH_ALERT_01")
                .uploadedBy(bankAdminUser)
                .fileReference("batch1.xlsx")
                .totalRecords(2)
                .alertsGeneratedCount(2)
                .status(BatchStatus.PROCESSED_ALERTS_GENERATED)
                .build());

        FinancialTransaction txn1 = financialTransactionRepository.save(FinancialTransaction.builder()
                .batch(batch)
                .txnNo("TXN_10001")
                .originatorAccount(acc)
                .amount(new BigDecimal("9500.00"))
                .currency("INR")
                .txnType(TransactionType.CASH)
                .direction(TransactionDirection.IN)
                .counterpartyName("Unknown Counterparty")
                .counterpartyAccountNo("9988776655")
                .counterpartyBank("HDFC Bank")
                .counterpartyCountryCode("IN")
                .txnTimestamp(LocalDateTime.now())
                .countryCode("IN")
                .build());

        FinancialTransaction txn2 = financialTransactionRepository.save(FinancialTransaction.builder()
                .batch(batch)
                .txnNo("TXN_10002")
                .originatorAccount(acc)
                .amount(new BigDecimal("450000.00"))
                .currency("INR")
                .txnType(TransactionType.NEFT)
                .direction(TransactionDirection.OUT)
                .counterpartyName("Foreign Account Ltd")
                .counterpartyAccountNo("1122334455")
                .counterpartyBank("Citi Bank")
                .counterpartyCountryCode("US")
                .txnTimestamp(LocalDateTime.now())
                .countryCode("IN")
                .build());

        Alert alertMed = alertRepository.save(Alert.builder()
                .alertCode("ALT-MED-01")
                .transaction(txn1)
                .rule(medRule)
                .severity(AlertSeverity.MEDIUM)
                .alertStatus(AlertStatus.OPEN)
                .build());

        Alert alertHigh = alertRepository.save(Alert.builder()
                .alertCode("ALT-HIGH-01")
                .transaction(txn2)
                .rule(highRule)
                .severity(AlertSeverity.HIGH)
                .alertStatus(AlertStatus.OPEN)
                .build());

        TenantContext.clear();

        // 1. Fetch Alert Stats
        MvcResult statsResult = mockMvc.perform(get("/api/v1/bank/admin/alerts/stats")
                        .header("Authorization", "Bearer " + bankAdminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode statsRes = objectMapper.readTree(statsResult.getResponse().getContentAsString());
        assertThat(statsRes.get("highSeverityCount").asLong()).isEqualTo(1);
        assertThat(statsRes.get("mediumSeverityCount").asLong()).isEqualTo(1);
        assertThat(statsRes.get("openAlertsCount").asLong()).isEqualTo(2);
        assertThat(statsRes.get("totalAlertsCount").asLong()).isEqualTo(2);

        // 2. Fetch Alerts Dashboard (Sorted HIGH first)
        MvcResult alertsResult = mockMvc.perform(get("/api/v1/bank/admin/alerts")
                        .header("Authorization", "Bearer " + bankAdminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode alertsRes = objectMapper.readTree(alertsResult.getResponse().getContentAsString());
        assertThat(alertsRes.get("content").isArray()).isTrue();
        assertThat(alertsRes.get("content").size()).isEqualTo(2);
        // First element should be HIGH severity alert
        assertThat(alertsRes.get("content").get(0).get("severity").asText()).isEqualTo("HIGH");
        assertThat(alertsRes.get("content").get(0).get("alertCode").asText()).isEqualTo("ALT-HIGH-01");

        // 3. Fetch Alert Detail
        MvcResult detailResult = mockMvc.perform(get("/api/v1/bank/admin/alerts/" + alertHigh.getAlertId())
                        .header("Authorization", "Bearer " + bankAdminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode detailRes = objectMapper.readTree(detailResult.getResponse().getContentAsString());
        assertThat(detailRes.get("alertCode").asText()).isEqualTo("ALT-HIGH-01");
        assertThat(detailRes.get("transactionTxnNo").asText()).isEqualTo("TXN_10002");
        assertThat(detailRes.get("counterpartyName").asText()).isEqualTo("Foreign Account Ltd");
        assertThat(detailRes.get("ruleCode").asText()).isEqualTo("RULE_HIGH_01");
    }

    @Test
    @DisplayName("BankAdmin can assign alerts to CO, create Case, track workload, and reassign Case")
    void caseAssignmentAndReassignmentFlow() throws Exception {
        // Create 2 Compliance Officers
        ComplianceOfficerRequest coReq1 = new ComplianceOfficerRequest();
        coReq1.setUserCode("CO_ALERT_01");
        coReq1.setEmployeeId("EMP_ALERT_01");
        coReq1.setFirstName("Officer");
        coReq1.setLastName("Alpha");
        coReq1.setEmail("co_alert_1@bank.com");
        coReq1.setPhoneNumber("9991112223");

        MvcResult co1Result = mockMvc.perform(post("/api/v1/bank/admin/add-compliance-officer")
                        .header("Authorization", "Bearer " + bankAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(coReq1)))
                .andExpect(status().isCreated())
                .andReturn();

        String co1Id = objectMapper.readTree(co1Result.getResponse().getContentAsString()).get("userId").asText();

        ComplianceOfficerRequest coReq2 = new ComplianceOfficerRequest();
        coReq2.setUserCode("CO_ALERT_02");
        coReq2.setEmployeeId("EMP_ALERT_02");
        coReq2.setFirstName("Officer");
        coReq2.setLastName("Beta");
        coReq2.setEmail("co_alert_2@bank.com");
        coReq2.setPhoneNumber("9991112224");

        MvcResult co2Result = mockMvc.perform(post("/api/v1/bank/admin/add-compliance-officer")
                        .header("Authorization", "Bearer " + bankAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(coReq2)))
                .andExpect(status().isCreated())
                .andReturn();

        String co2Id = objectMapper.readTree(co2Result.getResponse().getContentAsString()).get("userId").asText();

        // Seed transaction and alert
        TenantContext.setCurrentTenant(testTenant.getSchemaName());
        Account acc = accountRepository.save(Account.builder()
                .accountNumber("ACC_2002")
                .accountHolderName("Alice Smith Test")
                .accountType(com.tss.aml.enums.AccountType.SAVINGS)
                .countryCode("IN")
                .openedAt(LocalDateTime.now())
                .build());

        TransactionBatch batch = transactionBatchRepository.save(TransactionBatch.builder()
                .batchCode("BATCH_ALERT_02")
                .uploadedBy(bankAdminUser)
                .fileReference("batch2.xlsx")
                .totalRecords(1)
                .alertsGeneratedCount(1)
                .status(BatchStatus.PROCESSED_ALERTS_GENERATED)
                .build());

        FinancialTransaction txn = financialTransactionRepository.save(FinancialTransaction.builder()
                .batch(batch)
                .txnNo("TXN_20001")
                .originatorAccount(acc)
                .amount(new BigDecimal("12000.00"))
                .currency("INR")
                .txnType(TransactionType.RTGS)
                .direction(TransactionDirection.OUT)
                .counterpartyName("Offshore Corp")
                .counterpartyAccountNo("9900112233")
                .counterpartyBank("Barclays")
                .counterpartyCountryCode("UK")
                .txnTimestamp(LocalDateTime.now())
                .countryCode("IN")
                .build());

        Alert alert = alertRepository.save(Alert.builder()
                .alertCode("ALT-ASSIGN-01")
                .transaction(txn)
                .rule(highRule)
                .severity(AlertSeverity.HIGH)
                .alertStatus(AlertStatus.OPEN)
                .build());
        TenantContext.clear();

        // Check Workload before assignment
        MvcResult initialWorkload = mockMvc.perform(get("/api/v1/bank/admin/compliance-officers/workload")
                        .header("Authorization", "Bearer " + bankAdminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode initialWlRes = objectMapper.readTree(initialWorkload.getResponse().getContentAsString());
        assertThat(initialWlRes.isArray()).isTrue();

        // Assign alert to CO1
        CreateCaseRequest assignReq = new CreateCaseRequest();
        assignReq.setAlertIds(List.of(alert.getAlertId()));
        assignReq.setAssigneeId(UUID.fromString(co1Id));
        assignReq.setInitialNote("High severity structuring alert - urgent investigation required.");

        MvcResult assignResult = mockMvc.perform(post("/api/v1/bank/admin/cases/assign")
                        .header("Authorization", "Bearer " + bankAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode assignRes = objectMapper.readTree(assignResult.getResponse().getContentAsString());
        String caseId = assignRes.get("caseId").asText();
        assertThat(assignRes.get("caseCode").asText()).startsWith("CASE-");
        assertThat(assignRes.get("status").asText()).isEqualTo("OPEN");
        assertThat(assignRes.get("assignedToId").asText()).isEqualTo(co1Id);
        assertThat(assignRes.get("alertCount").asInt()).isEqualTo(1);

        // Verify notification and audit log in tenant DB
        TenantContext.setCurrentTenant(testTenant.getSchemaName());
        List<Notification> notifications = notificationRepository.findByRecipient_UserIdOrderByCreatedAtDesc(UUID.fromString(co1Id));
        assertThat(notifications).isNotEmpty();
        assertThat(notifications.get(0).getMessage()).contains("New case assigned to you");

        List<AuditLog> auditLogs = auditLogRepository.findByEntityTypeAndEntityId("CASE", caseId);
        assertThat(auditLogs).isNotEmpty();
        assertThat(auditLogs.get(0).getAction()).isEqualTo("CASE_ASSIGNED");
        TenantContext.clear();

        // Check Workload after assignment (CO1 should have 1 active case)
        MvcResult updatedWorkload = mockMvc.perform(get("/api/v1/bank/admin/compliance-officers/workload")
                        .header("Authorization", "Bearer " + bankAdminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode updatedWlRes = objectMapper.readTree(updatedWorkload.getResponse().getContentAsString());
        for (JsonNode node : updatedWlRes) {
            if (node.get("userId").asText().equals(co1Id)) {
                assertThat(node.get("activeCaseCount").asLong()).isEqualTo(1L);
            }
        }

        // Reassign Case from CO1 to CO2
        ReassignCaseRequest reassignReq = new ReassignCaseRequest();
        reassignReq.setNewAssigneeId(UUID.fromString(co2Id));
        reassignReq.setReason("CO1 on annual leave.");

        MvcResult reassignResult = mockMvc.perform(post("/api/v1/bank/admin/cases/" + caseId + "/reassign")
                        .header("Authorization", "Bearer " + bankAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reassignReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode reassignRes = objectMapper.readTree(reassignResult.getResponse().getContentAsString());
        assertThat(reassignRes.get("assignedToId").asText()).isEqualTo(co2Id);

        // Fetch Case Tracking Board
        MvcResult casesResult = mockMvc.perform(get("/api/v1/bank/admin/cases")
                        .header("Authorization", "Bearer " + bankAdminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode casesRes = objectMapper.readTree(casesResult.getResponse().getContentAsString());
        assertThat(casesRes.get("content").isArray()).isTrue();
        assertThat(casesRes.get("content").size()).isEqualTo(1);
        assertThat(casesRes.get("content").get(0).get("assignedToId").asText()).isEqualTo(co2Id);
    }
}
