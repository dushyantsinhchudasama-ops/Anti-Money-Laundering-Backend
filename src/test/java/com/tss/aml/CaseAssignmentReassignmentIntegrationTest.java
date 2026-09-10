package com.tss.aml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.aml.config.SystemAdminDataInitializer;
import com.tss.aml.dtos.tenant.CreateCaseRequest;
import com.tss.aml.dtos.tenant.ReassignCaseRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class CaseAssignmentReassignmentIntegrationTest {

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
        private AuditLogRepository auditLogRepository;

        @Autowired
        private com.tss.aml.repositories.NotificationRepository notificationRepository;

        @Autowired
        private CustomUserDetailsService customUserDetailsService;

        @Autowired
        private JwtTokenProvider jwtTokenProvider;

        @Autowired
        private PasswordEncoder passwordEncoder;

        private Tenant tenantHdfc;
        private Tenant tenantIcici;

        private Users hdfcBankAdmin;
        private Users hdfcCoA;
        private Users hdfcCoB;
        private Users hdfcCoC;
        private Users hdfcInactiveCo;
        private Users iciciBankAdmin;

        private String hdfcBankAdminToken;
        private String hdfcCoAToken;
        private String iciciBankAdminToken;

        private Alert unassignedAlert;

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

                // Clear tenant tables
                TenantContext.setCurrentTenant("tenant_hdfc");
                notificationRepository.deleteAll();
                auditLogRepository.deleteAll();
                alertRepository.deleteAll();
                caseRepository.deleteAll();
                txnRepository.deleteAll();
                batchRepository.deleteAll();
                accountRepository.deleteAll();
                TenantContext.clear();

                TenantContext.setCurrentTenant("tenant_icici");
                notificationRepository.deleteAll();
                auditLogRepository.deleteAll();
                alertRepository.deleteAll();
                caseRepository.deleteAll();
                txnRepository.deleteAll();
                batchRepository.deleteAll();
                accountRepository.deleteAll();
                TenantContext.clear();

                userRepository.deleteAll();

                // Create HDFC Users
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

                hdfcCoC = userRepository.save(Users.builder()
                                .tenant(tenantHdfc)
                                .userCode("HDFC_CO_C")
                                .role(UserRole.COMPLIANCE_OFFICER)
                                .firstName("CO")
                                .lastName("Gamma")
                                .email("coc@hdfc.com")
                                .passwordHash(passwordEncoder.encode("Password123!"))
                                .isActive(true)
                                .mustResetPassword(false)
                                .build());

                hdfcInactiveCo = userRepository.save(Users.builder()
                                .tenant(tenantHdfc)
                                .userCode("HDFC_CO_INACTIVE")
                                .role(UserRole.COMPLIANCE_OFFICER)
                                .firstName("CO")
                                .lastName("Inactive")
                                .email("coinactive@hdfc.com")
                                .passwordHash(passwordEncoder.encode("Password123!"))
                                .isActive(false)
                                .mustResetPassword(false)
                                .build());

                iciciBankAdmin = userRepository.save(Users.builder()
                                .tenant(tenantIcici)
                                .userCode("ICICI_ADMIN")
                                .role(UserRole.BANK_ADMIN)
                                .firstName("ICICI")
                                .lastName("Admin")
                                .email("admin@icici.com")
                                .passwordHash(passwordEncoder.encode("Password123!"))
                                .isActive(true)
                                .mustResetPassword(false)
                                .build());

                // Generate Tokens
                hdfcBankAdminToken = generateUserToken(hdfcBankAdmin);
                hdfcCoAToken = generateUserToken(hdfcCoA);
                iciciBankAdminToken = generateUserToken(iciciBankAdmin);

                // Setup Tenant HDFC entities
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

                FinancialTransaction txn = txnRepository.save(FinancialTransaction.builder()
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

                unassignedAlert = alertRepository.save(Alert.builder()
                                .alertCode("ALT-UNASSIGNED")
                                .transaction(txn)
                                .rule(testRule)
                                .severity(AlertSeverity.HIGH)
                                .alertStatus(AlertStatus.OPEN)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build());

                TenantContext.clear();
        }

        private String generateUserToken(Users user) {
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(user.getEmail());
                Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null,
                                userDetails.getAuthorities());
                return jwtTokenProvider.generateToken(auth);
        }

        @Test
        @DisplayName("ASSIGN: Assign unassigned alert/case to CO-A creates case, sets assignedTo, and logs CASE_ASSIGNED")
        void assignUnassignedCaseSuccessTest() throws Exception {
                CreateCaseRequest request = CreateCaseRequest.builder()
                                .alertIds(List.of(unassignedAlert.getAlertId()))
                                .assigneeId(hdfcCoA.getUserId())
                                .initialNote("Initial review assignment")
                                .build();

                MvcResult result = mockMvc.perform(post("/api/v1/bank/admin/cases/assign")
                                .header("Authorization", "Bearer " + hdfcBankAdminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn();

                JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
                UUID caseId = UUID.fromString(response.get("caseId").asText());

                // Verify DB State
                TenantContext.setCurrentTenant("tenant_hdfc");
                AmlCase caseInDb = caseRepository.findById(caseId).orElseThrow();
                assertThat(caseInDb.getAssignedTo()).isNotNull();
                assertThat(caseInDb.getAssignedTo().getUserId()).isEqualTo(hdfcCoA.getUserId());

                // Verify Audit Log
                List<AuditLog> auditLogs = auditLogRepository.findAll();
                boolean auditFound = auditLogs.stream().anyMatch(log -> "CASE_ASSIGNED".equals(log.getAction()) &&
                                "CASE".equals(log.getEntityType()) &&
                                caseId.toString().equals(log.getEntityId()) &&
                                log.getDetails().contains(hdfcCoA.getEmail()));
                assertThat(auditFound).isTrue();
                TenantContext.clear();
        }

        @Test
        @DisplayName("ASSIGN: Assigning case to an inactive Compliance Officer is rejected with 400 Bad Request")
        void assignToInactiveOfficerRejectedTest() throws Exception {
                CreateCaseRequest request = CreateCaseRequest.builder()
                                .alertIds(List.of(unassignedAlert.getAlertId()))
                                .assigneeId(hdfcInactiveCo.getUserId())
                                .build();

                mockMvc.perform(post("/api/v1/bank/admin/cases/assign")
                                .header("Authorization", "Bearer " + hdfcBankAdminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("ASSIGN: Assigning case to a user without COMPLIANCE_OFFICER role is rejected")
        void assignToNonCOUserRejectedTest() throws Exception {
                CreateCaseRequest request = CreateCaseRequest.builder()
                                .alertIds(List.of(unassignedAlert.getAlertId()))
                                .assigneeId(hdfcBankAdmin.getUserId())
                                .build();

                mockMvc.perform(post("/api/v1/bank/admin/cases/assign")
                                .header("Authorization", "Bearer " + hdfcBankAdminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("REASSIGN: Reassigning assigned case from CO-A to CO-B updates assignedTo and logs CASE_REASSIGNED preserving history")
        void reassignAssignedCaseSuccessTest() throws Exception {
                // 1. Initial Assign to CO-A
                TenantContext.setCurrentTenant("tenant_hdfc");
                AmlCase amlCase = caseRepository.save(AmlCase.builder()
                                .caseCode("CASE-TEST-01")
                                .createdBy(hdfcBankAdmin)
                                .assignedTo(hdfcCoA)
                                .status(CaseStatus.OPEN)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build());
                UUID caseId = amlCase.getCaseId();
                TenantContext.clear();

                // 2. Reassign to CO-B
                ReassignCaseRequest request = ReassignCaseRequest.builder()
                                .newAssigneeId(hdfcCoB.getUserId())
                                .reason("Workload balancing")
                                .build();

                mockMvc.perform(post("/api/v1/bank/admin/cases/" + caseId + "/reassign")
                                .header("Authorization", "Bearer " + hdfcBankAdminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk());

                // 3. Verify assignedTo is explicitly updated to CO-B
                TenantContext.setCurrentTenant("tenant_hdfc");
                AmlCase updatedCase = caseRepository.findById(caseId).orElseThrow();
                assertThat(updatedCase.getAssignedTo().getUserId()).isEqualTo(hdfcCoB.getUserId());

                // 4. Verify CASE_REASSIGNED audit log preserves previous CO-A and new CO-B
                List<AuditLog> auditLogs = auditLogRepository.findAll();
                boolean reassignAuditFound = auditLogs.stream()
                                .anyMatch(log -> "CASE_REASSIGNED".equals(log.getAction()) &&
                                                "CASE".equals(log.getEntityType()) &&
                                                caseId.toString().equals(log.getEntityId()) &&
                                                log.getDetails().contains(hdfcCoA.getEmail()) &&
                                                log.getDetails().contains(hdfcCoB.getEmail()));
                assertThat(reassignAuditFound).isTrue();
                TenantContext.clear();
        }

        @Test
        @DisplayName("REASSIGN: Multiple reassignments (CO-A -> CO-B -> CO-C) preserve complete immutable audit trail")
        void multipleReassignmentsPreservesHistoryTest() throws Exception {
                TenantContext.setCurrentTenant("tenant_hdfc");
                AmlCase amlCase = caseRepository.save(AmlCase.builder()
                                .caseCode("CASE-TEST-02")
                                .createdBy(hdfcBankAdmin)
                                .assignedTo(hdfcCoA)
                                .status(CaseStatus.OPEN)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build());
                UUID caseId = amlCase.getCaseId();

                // Initial assignment audit
                auditLogRepository.save(AuditLog.builder()
                                .actor(hdfcBankAdmin)
                                .action("CASE_ASSIGNED")
                                .entityType("CASE")
                                .entityId(caseId.toString())
                                .details("Assigned case to " + hdfcCoA.getEmail())
                                .build());
                TenantContext.clear();

                // Reassign 1: CO-A -> CO-B
                ReassignCaseRequest req1 = ReassignCaseRequest.builder()
                                .newAssigneeId(hdfcCoB.getUserId())
                                .reason("First transfer")
                                .build();
                mockMvc.perform(post("/api/v1/bank/admin/cases/" + caseId + "/reassign")
                                .header("Authorization", "Bearer " + hdfcBankAdminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req1)))
                                .andExpect(status().isOk());

                // Reassign 2: CO-B -> CO-C
                ReassignCaseRequest req2 = ReassignCaseRequest.builder()
                                .newAssigneeId(hdfcCoC.getUserId())
                                .reason("Second transfer")
                                .build();
                mockMvc.perform(post("/api/v1/bank/admin/cases/" + caseId + "/reassign")
                                .header("Authorization", "Bearer " + hdfcBankAdminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req2)))
                                .andExpect(status().isOk());

                // Verify state is CO-C
                TenantContext.setCurrentTenant("tenant_hdfc");
                AmlCase updatedCase = caseRepository.findById(caseId).orElseThrow();
                assertThat(updatedCase.getAssignedTo().getUserId()).isEqualTo(hdfcCoC.getUserId());

                // Verify audit logs history: 1 CASE_ASSIGNED + 2 CASE_REASSIGNED
                List<AuditLog> auditLogs = auditLogRepository.findAll();
                long assignedCount = auditLogs.stream().filter(l -> "CASE_ASSIGNED".equals(l.getAction())).count();
                long reassignedCount = auditLogs.stream().filter(l -> "CASE_REASSIGNED".equals(l.getAction())).count();

                assertThat(assignedCount).isEqualTo(1);
                assertThat(reassignedCount).isEqualTo(2);
                TenantContext.clear();
        }

        @Test
        @DisplayName("REASSIGN: Reassigning an unassigned case is rejected")
        void reassignUnassignedCaseRejectedTest() throws Exception {
                TenantContext.setCurrentTenant("tenant_hdfc");
                AmlCase unassignedCase = caseRepository.save(AmlCase.builder()
                                .caseCode("CASE-UNASSIGNED")
                                .createdBy(hdfcBankAdmin)
                                .assignedTo(null)
                                .status(CaseStatus.OPEN)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build());
                UUID caseId = unassignedCase.getCaseId();
                TenantContext.clear();

                ReassignCaseRequest request = ReassignCaseRequest.builder()
                                .newAssigneeId(hdfcCoB.getUserId())
                                .reason("Assign unassigned")
                                .build();

                mockMvc.perform(post("/api/v1/bank/admin/cases/" + caseId + "/reassign")
                                .header("Authorization", "Bearer " + hdfcBankAdminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("REASSIGN: Reassigning a case to the current Compliance Officer is rejected with 400 Bad Request")
        void reassignToSameOfficerRejectedTest() throws Exception {
                TenantContext.setCurrentTenant("tenant_hdfc");
                AmlCase amlCase = caseRepository.save(AmlCase.builder()
                                .caseCode("CASE-SAME-CO")
                                .createdBy(hdfcBankAdmin)
                                .assignedTo(hdfcCoA)
                                .status(CaseStatus.OPEN)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build());
                UUID caseId = amlCase.getCaseId();
                TenantContext.clear();

                ReassignCaseRequest request = ReassignCaseRequest.builder()
                                .newAssigneeId(hdfcCoA.getUserId())
                                .reason("Same CO assignment")
                                .build();

                mockMvc.perform(post("/api/v1/bank/admin/cases/" + caseId + "/reassign")
                                .header("Authorization", "Bearer " + hdfcBankAdminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("REASSIGN: Reassigning to an inactive Compliance Officer is rejected with 400 Bad Request")
        void reassignToInactiveOfficerRejectedTest() throws Exception {
                TenantContext.setCurrentTenant("tenant_hdfc");
                AmlCase amlCase = caseRepository.save(AmlCase.builder()
                                .caseCode("CASE-INACTIVE")
                                .createdBy(hdfcBankAdmin)
                                .assignedTo(hdfcCoA)
                                .status(CaseStatus.OPEN)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build());
                UUID caseId = amlCase.getCaseId();
                TenantContext.clear();

                ReassignCaseRequest request = ReassignCaseRequest.builder()
                                .newAssigneeId(hdfcInactiveCo.getUserId())
                                .build();

                mockMvc.perform(post("/api/v1/bank/admin/cases/" + caseId + "/reassign")
                                .header("Authorization", "Bearer " + hdfcBankAdminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("SECURITY: Compliance Officer role cannot assign or reassign cases (403 Forbidden)")
        void securityRoleAuthorizationTest() throws Exception {
                // CO attempts to assign
                CreateCaseRequest assignReq = CreateCaseRequest.builder()
                                .alertIds(List.of(unassignedAlert.getAlertId()))
                                .assigneeId(hdfcCoA.getUserId())
                                .build();

                mockMvc.perform(post("/api/v1/bank/admin/cases/assign")
                                .header("Authorization", "Bearer " + hdfcCoAToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(assignReq)))
                                .andExpect(status().isForbidden());

                // CO attempts to reassign
                ReassignCaseRequest reassignReq = ReassignCaseRequest.builder()
                                .newAssigneeId(hdfcCoB.getUserId())
                                .build();

                mockMvc.perform(post("/api/v1/bank/admin/cases/" + UUID.randomUUID() + "/reassign")
                                .header("Authorization", "Bearer " + hdfcCoAToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(reassignReq)))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("CROSS-TENANT ISOLATION: Bank Admin from ICICI cannot assign or reassign HDFC case/user")
        void crossTenantIsolationTest() throws Exception {
                // ICICI Bank Admin attempts to assign HDFC alert/CO
                CreateCaseRequest assignReq = CreateCaseRequest.builder()
                                .alertIds(List.of(unassignedAlert.getAlertId()))
                                .assigneeId(hdfcCoA.getUserId())
                                .build();

                mockMvc.perform(post("/api/v1/bank/admin/cases/assign")
                                .header("Authorization", "Bearer " + iciciBankAdminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(assignReq)))
                                .andExpect(status().isNotFound()); // User not found in ICICI tenant scope

                // ICICI Bank Admin attempts to reassign HDFC case
                TenantContext.setCurrentTenant("tenant_hdfc");
                AmlCase hdfcCase = caseRepository.save(AmlCase.builder()
                                .caseCode("CASE-HDFC-CROSS")
                                .createdBy(hdfcBankAdmin)
                                .assignedTo(hdfcCoA)
                                .status(CaseStatus.OPEN)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build());
                UUID caseId = hdfcCase.getCaseId();
                TenantContext.clear();

                ReassignCaseRequest reassignReq = ReassignCaseRequest.builder()
                                .newAssigneeId(hdfcCoB.getUserId())
                                .build();

                mockMvc.perform(post("/api/v1/bank/admin/cases/" + caseId + "/reassign")
                                .header("Authorization", "Bearer " + iciciBankAdminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(reassignReq)))
                                .andExpect(status().isNotFound()); // Case not found in ICICI tenant schema
        }
}
