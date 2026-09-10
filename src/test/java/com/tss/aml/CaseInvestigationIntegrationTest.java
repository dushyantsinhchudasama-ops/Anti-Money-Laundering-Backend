package com.tss.aml;

import com.tss.aml.config.SystemAdminDataInitializer;
import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.entities.system.Tenant;
import com.tss.aml.entities.system.Users;
import com.tss.aml.entities.tenant.Account;
import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.entities.tenant.AmlCase;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.entities.tenant.TransactionBatch;
import com.tss.aml.enums.AccountType;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.AlertStatus;
import com.tss.aml.enums.BatchStatus;
import com.tss.aml.enums.CaseStatus;
import com.tss.aml.enums.RiskRating;
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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("dev")
public class CaseInvestigationIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

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

    private AmlCase primaryCase;
    private AmlCase historicalCase;
    private Account primaryAccount;
    private Account linkedAccount;
    private TransactionBatch batch;
    private FinancialTransaction triggeringTxn;
    private FinancialTransaction relatedTxnInBatch;
    private FinancialTransaction historicalTxn;

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
                .userCode("HDFC_ADMIN_INV")
                .role(UserRole.BANK_ADMIN)
                .email("admin_inv@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("HDFC").lastName("Admin")
                .isActive(true)
                .mustResetPassword(false)
                .build());

        hdfcCoA = userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_CO_A_INV")
                .role(UserRole.COMPLIANCE_OFFICER)
                .email("coa_inv@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Officer").lastName("A")
                .isActive(true)
                .mustResetPassword(false)
                .build());

        hdfcCoB = userRepository.save(Users.builder()
                .tenant(tenantHdfc)
                .userCode("HDFC_CO_B_INV")
                .role(UserRole.COMPLIANCE_OFFICER)
                .email("cob_inv@hdfc.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Officer").lastName("B")
                .isActive(true)
                .mustResetPassword(false)
                .build());

        iciciCo = userRepository.save(Users.builder()
                .tenant(tenantIcici)
                .userCode("ICICI_CO_INV")
                .role(UserRole.COMPLIANCE_OFFICER)
                .email("co_inv@icici.com")
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
                .ruleCode("RULE_STRUCT_01")
                .ruleName("Structuring Alert Rule")
                .description("Detects transactions just below reporting threshold")
                .typology(RuleTypology.STRUCTURING_SMURFING)
                .parameters(java.util.Map.of("amountThreshold", 9000))
                .defaultSeverity(RuleSeverity.HIGH)
                .status(RuleStatus.ACTIVE)
                .build()) : allRules.get(0);

        batch = batchRepository.save(TransactionBatch.builder()
                .batchCode("BATCH-INV-001")
                .uploadedBy(hdfcBankAdmin)
                .fileReference("batch_inv_001.csv")
                .status(BatchStatus.PROCESSED_ALERTS_GENERATED)
                .totalRecords(2)
                .uploadedAt(LocalDateTime.now().minusDays(2))
                .build());

        primaryAccount = accountRepository.save(Account.builder()
                .accountNumber("ACC-PRIMARY-999")
                .accountHolderName("John Doe")
                .accountType(AccountType.SAVINGS)
                .bankName("HDFC Bank")
                .countryCode("US")
                .riskRating(RiskRating.HIGH)
                .openedAt(LocalDateTime.now().minusYears(3))
                .build());

        linkedAccount = accountRepository.save(Account.builder()
                .accountNumber("ACC-LINKED-888")
                .accountHolderName("John Doe") // Same holder name -> Linked account
                .accountType(AccountType.CURRENT)
                .bankName("HDFC Bank")
                .countryCode("US")
                .riskRating(RiskRating.MEDIUM)
                .openedAt(LocalDateTime.now().minusYears(1))
                .build());

        triggeringTxn = txnRepository.save(FinancialTransaction.builder()
                .batch(batch)
                .txnNo("TXN-TRIG-001")
                .originatorAccount(primaryAccount)
                .amount(new BigDecimal("9800.00"))
                .currency("USD")
                .txnType(TransactionType.NEFT)
                .direction(TransactionDirection.OUT)
                .counterpartyName("Offshore Ltd")
                .counterpartyAccountNo("CP-ACC-111")
                .counterpartyBank("Cayman Bank")
                .counterpartyCountryCode("KY")
                .txnTimestamp(LocalDateTime.now().minusHours(12))
                .countryCode("US")
                .build());

        relatedTxnInBatch = txnRepository.save(FinancialTransaction.builder()
                .batch(batch)
                .txnNo("TXN-BATCH-002")
                .originatorAccount(primaryAccount)
                .amount(new BigDecimal("4500.00"))
                .currency("USD")
                .txnType(TransactionType.CASH)
                .direction(TransactionDirection.IN)
                .counterpartyName("Local Shop")
                .counterpartyAccountNo("CP-ACC-222")
                .counterpartyBank("Local Bank")
                .counterpartyCountryCode("US")
                .txnTimestamp(LocalDateTime.now().minusHours(24))
                .countryCode("US")
                .build());

        historicalTxn = txnRepository.save(FinancialTransaction.builder()
                .batch(batch)
                .txnNo("TXN-HIST-003")
                .originatorAccount(primaryAccount)
                .amount(new BigDecimal("12000.00"))
                .currency("USD")
                .txnType(TransactionType.NEFT)
                .direction(TransactionDirection.OUT)
                .counterpartyName("Offshore Ltd") // Same counterparty for aggregation
                .counterpartyAccountNo("CP-ACC-111")
                .counterpartyBank("Cayman Bank")
                .counterpartyCountryCode("KY")
                .txnTimestamp(LocalDateTime.now().minusDays(30))
                .countryCode("US")
                .build());

        Alert primaryAlert = alertRepository.save(Alert.builder()
                .alertCode("ALT-TRIG-001")
                .transaction(triggeringTxn)
                .rule(structRule)
                .severity(AlertSeverity.HIGH)
                .alertStatus(AlertStatus.OPEN)
                .build());

        Alert historicalAlert = alertRepository.save(Alert.builder()
                .alertCode("ALT-HIST-002")
                .transaction(historicalTxn)
                .rule(structRule)
                .severity(AlertSeverity.MEDIUM)
                .alertStatus(AlertStatus.CLOSED)
                .build());

        primaryCase = caseRepository.save(AmlCase.builder()
                .caseCode("CASE-INV-001")
                .createdBy(hdfcBankAdmin)
                .assignedTo(hdfcCoA)
                .status(CaseStatus.IN_PROGRESS)
                .build());

        primaryAlert.setAmlCase(primaryCase);
        alertRepository.save(primaryAlert);

        historicalCase = caseRepository.save(AmlCase.builder()
                .caseCode("CASE-HIST-002")
                .createdBy(hdfcBankAdmin)
                .assignedTo(hdfcCoA)
                .status(CaseStatus.CLOSED_NO_ACTION)
                .build());

        historicalAlert.setAmlCase(historicalCase);
        alertRepository.save(historicalAlert);

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
    @DisplayName("Assigned CO can successfully retrieve comprehensive investigation data")
    void assignedCOCanRetrieveInvestigationDataSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/cases/" + primaryCase.getCaseId() + "/investigation")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc"))
                .andExpect(status().isOk())
                // Case Summary
                .andExpect(jsonPath("$.caseSummary.caseId").value(primaryCase.getCaseId().toString()))
                .andExpect(jsonPath("$.caseSummary.caseCode").value("CASE-INV-001"))
                .andExpect(jsonPath("$.caseSummary.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.caseSummary.assignedToEmail").value("coa_inv@hdfc.com"))
                // Triggering Transactions
                .andExpect(jsonPath("$.triggeringTransactions", hasSize(1)))
                .andExpect(jsonPath("$.triggeringTransactions[0].txnNo").value("TXN-TRIG-001"))
                .andExpect(jsonPath("$.triggeringTransactions[0].amount").value(9800.00))
                .andExpect(jsonPath("$.triggeringTransactions[0].alertCode").value("ALT-TRIG-001"))
                .andExpect(jsonPath("$.triggeringTransactions[0].batchCode").value("BATCH-INV-001"))
                // Customer Profile
                .andExpect(jsonPath("$.customerAccountProfile.accountNumber").value("ACC-PRIMARY-999"))
                .andExpect(jsonPath("$.customerAccountProfile.accountHolderName").value("John Doe"))
                .andExpect(jsonPath("$.customerAccountProfile.riskRating").value("HIGH"))
                // Related Transactions
                .andExpect(jsonPath("$.relatedTransactions", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.relatedTransactions[0].txnNo").value("TXN-BATCH-002"))
                // Customer Transaction History
                .andExpect(jsonPath("$.customerTransactionHistory.content", hasSize(greaterThanOrEqualTo(2))))
                // Counterparties
                .andExpect(jsonPath("$.counterparties", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.counterparties[0].counterpartyName").value("Offshore Ltd"))
                // Linked Accounts
                .andExpect(jsonPath("$.linkedAccounts", hasSize(1)))
                .andExpect(jsonPath("$.linkedAccounts[0].accountNumber").value("ACC-LINKED-888"))
                // Historical Alerts
                .andExpect(jsonPath("$.historicalAlerts", hasSize(2)))
                // Historical Cases
                .andExpect(jsonPath("$.historicalCases", hasSize(1)))
                .andExpect(jsonPath("$.historicalCases[0].caseCode").value("CASE-HIST-002"));
    }

    @Test
    @DisplayName("CO assigned to another case receives Access Denied (403)")
    void unauthorizedCOAccessDenied() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/cases/" + primaryCase.getCaseId() + "/investigation")
                        .header("Authorization", "Bearer " + hdfcCoBToken)
                        .header("X-Tenant-ID", "tenant_hdfc"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cross-tenant CO request receives Not Found (404)")
    void crossTenantIsolationEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/cases/" + primaryCase.getCaseId() + "/investigation")
                        .header("Authorization", "Bearer " + iciciCoToken)
                        .header("X-Tenant-ID", "tenant_icici"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Bank Admin accessing CO investigation endpoint receives 403 Forbidden")
    void bankAdminAccessDenied() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/cases/" + primaryCase.getCaseId() + "/investigation")
                        .header("Authorization", "Bearer " + hdfcBankAdminToken)
                        .header("X-Tenant-ID", "tenant_hdfc"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request to investigation endpoint is denied")
    void unauthenticatedRequestDenied() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/cases/" + primaryCase.getCaseId() + "/investigation")
                        .header("X-Tenant-ID", "tenant_hdfc"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Investigation view is strictly read-only and causes no entity or audit log mutations")
    void readOnlyBehaviorVerified() throws Exception {
        TenantContext.setCurrentTenant("tenant_hdfc");
        long initialAuditLogCount = auditLogRepository.count();
        long initialCaseCount = caseRepository.count();
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/compliance/cases/" + primaryCase.getCaseId() + "/investigation")
                        .header("Authorization", "Bearer " + hdfcCoAToken)
                        .header("X-Tenant-ID", "tenant_hdfc"))
                .andExpect(status().isOk());

        TenantContext.setCurrentTenant("tenant_hdfc");
        long finalAuditLogCount = auditLogRepository.count();
        long finalCaseCount = caseRepository.count();
        AmlCase currentCase = caseRepository.findById(primaryCase.getCaseId()).orElseThrow();
        TenantContext.clear();

        assertEquals(initialAuditLogCount, finalAuditLogCount, "Investigation request must not create audit logs");
        assertEquals(initialCaseCount, finalCaseCount, "Investigation request must not create new cases");
        assertEquals(CaseStatus.IN_PROGRESS, currentCase.getStatus(), "Investigation request must not alter case status");
    }
}
