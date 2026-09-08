package com.tss.aml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.aml.config.SystemAdminDataInitializer;
import com.tss.aml.dtos.rule.CreateRuleRequest;
import com.tss.aml.dtos.tenant.ComplianceOfficerRequest;
import com.tss.aml.dtos.tenant.CreateBankAdminRequest;
import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.entities.system.Tenant;
import com.tss.aml.entities.system.Users;
import com.tss.aml.enums.RuleSeverity;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.enums.TenantStatus;

import com.tss.aml.repositories.SystemAdminRepository;
import com.tss.aml.repositories.TenantRepository;
import com.tss.aml.repositories.UserRepository;
import com.tss.aml.security.CustomUserDetailsService;
import com.tss.aml.security.JwtTokenProvider;
import com.tss.aml.tenant.TenantContext;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class BankAdminManagementIntegrationTest {

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
    private com.tss.aml.services.TenantMigrationService tenantMigrationService;

    @Autowired
    private com.tss.aml.repositories.AmlCaseRepository amlCaseRepository;

    @Autowired
    private com.tss.aml.repositories.AlertRepository alertRepository;

    private SystemAdmin systemAdmin;
    private String systemAdminToken;
    private Tenant testTenant;
    private String bankAdminToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        TenantContext.clear();
        SecurityContextHolder.clearContext();

        try {
            TenantContext.setCurrentTenant("tenant_hdfc_mgmt");
            alertRepository.deleteAll();
            amlCaseRepository.deleteAll();
        } catch (Exception ignored) {
        } finally {
            TenantContext.clear();
        }

        userRepository.findByEmail("officer_mgmt@hdfc.com").ifPresent(userRepository::delete);
        userRepository.findByUserCode("OFFICER_001").ifPresent(userRepository::delete);
        userRepository.findByEmail("officer_reset@hdfc.com").ifPresent(userRepository::delete);
        userRepository.findByUserCode("OFFICER_RESET_01").ifPresent(userRepository::delete);
        userRepository.findByEmail("officer_deact@hdfc.com").ifPresent(userRepository::delete);
        userRepository.findByUserCode("OFFICER_DEACT_01").ifPresent(userRepository::delete);
        userRepository.findByEmail("admin_mgmt@hdfc.com").ifPresent(userRepository::delete);
        userRepository.findByUserCode("ADMIN_MGMT_001").ifPresent(userRepository::delete);

        if (systemAdminRepository.count() == 0) {
            initializer.run(null);
        }

        systemAdmin = systemAdminRepository.findAll().get(0);
        UserDetails adminDetails = customUserDetailsService.loadUserByUsername(systemAdmin.getEmail());
        Authentication auth = new UsernamePasswordAuthenticationToken(
                adminDetails, null, adminDetails.getAuthorities()
        );
        systemAdminToken = jwtTokenProvider.generateToken(auth);

        testTenant = tenantRepository.findByTenantCode("HDFC_MGMT")
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .tenantCode("HDFC_MGMT")
                        .tenantName("HDFC Management Bank")
                        .displayName("HDFC Management")
                        .schemaName("tenant_hdfc_mgmt")
                        .status(TenantStatus.ACTIVE)
                        .onboardedByAdmin(systemAdmin)
                        .build()));

        tenantMigrationService.migrateTenantSchema(testTenant.getSchemaName());

        CreateBankAdminRequest adminReq = new CreateBankAdminRequest();
        adminReq.setUserCode("ADMIN_MGMT_001");
        adminReq.setFirstName("HDFC");
        adminReq.setLastName("Admin");
        adminReq.setEmail("admin_mgmt@hdfc.com");
        SecurityContextHolder.getContext().setAuthentication(auth);
        tenantService.createBankAdmin(testTenant.getTenantId(), adminReq);
        SecurityContextHolder.clearContext();

        userRepository.findByEmail("admin_mgmt@hdfc.com").ifPresent(u -> {
            u.setMustResetPassword(false);
            userRepository.save(u);
        });

        UserDetails bankAdminDetails = customUserDetailsService.loadUserByUsername("admin_mgmt@hdfc.com");
        Authentication bankAuth = new UsernamePasswordAuthenticationToken(
                bankAdminDetails, null, bankAdminDetails.getAuthorities()
        );
        bankAdminToken = jwtTokenProvider.generateToken(bankAuth);
    }

    @Test
    @DisplayName("SystemAdmin can add a new AML Rule successfully")
    void systemAdminCanAddNewRule() throws Exception {
        CreateRuleRequest ruleRequest = new CreateRuleRequest();
        ruleRequest.setRuleName("Test Round Amount Rule");
        ruleRequest.setDescription("Detects round amount transfers");
        ruleRequest.setTypology(RuleTypology.ROUND_AMOUNT_FLAGGING);
        ruleRequest.setParameters(objectMapper.readValue("{\"moduloThreshold\": 50000}", java.util.Map.class));
        ruleRequest.setDefaultSeverity(RuleSeverity.HIGH);

        MvcResult result = mockMvc.perform(post("/api/v1/system/admin/rules")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ruleRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("ruleId")).isNotNull();
        assertThat(response.get("ruleName").asText()).isEqualTo("Test Round Amount Rule");
        assertThat(response.get("typology").asText()).isEqualTo("ROUND_AMOUNT_FLAGGING");
    }

    @Test
    @DisplayName("BankAdmin can create, fetch, and list Compliance Officers")
    void bankAdminComplianceOfficerLifecycle() throws Exception {
        // 1. Create Compliance Officer
        ComplianceOfficerRequest officerRequest = new ComplianceOfficerRequest();
        officerRequest.setUserCode("OFFICER_001");
        officerRequest.setEmployeeId("EMP_OFFICER_01");
        officerRequest.setFirstName("Compliance");
        officerRequest.setLastName("Officer1");
        officerRequest.setEmail("officer_mgmt@hdfc.com");
        officerRequest.setPhoneNumber("9876543210");

        MvcResult createResult = mockMvc.perform(post("/api/v1/bank/admin/add-compliance-officer")
                        .header("Authorization", "Bearer " + bankAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(officerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String officerId = createResponse.get("userId").asText();
        assertThat(createResponse.get("userCode").asText()).isEqualTo("OFFICER_001");
        assertThat(createResponse.get("role").asText()).isEqualTo("COMPLIANCE_OFFICER");
        assertThat(createResponse.get("temporaryPassword").asText()).startsWith("TmpOff@");

        // 2. Fetch Compliance Officer by ID
        MvcResult getResult = mockMvc.perform(get("/api/v1/bank/admin/get-officer/" + officerId)
                        .header("Authorization", "Bearer " + bankAdminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode getResponse = objectMapper.readTree(getResult.getResponse().getContentAsString());
        assertThat(getResponse.get("email").asText()).isEqualTo("officer_mgmt@hdfc.com");

        // 3. List Compliance Officers for Tenant
        MvcResult listResult = mockMvc.perform(get("/api/v1/bank/admin/compliance-officers")
                        .header("Authorization", "Bearer " + bankAdminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode listResponse = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(listResponse.get("content").isArray()).isTrue();
        assertThat(listResponse.get("content").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("BankAdmin can reset Compliance Officer password and issue new temporary credentials")
    void bankAdminCanResetComplianceOfficerPassword() throws Exception {
        ComplianceOfficerRequest officerRequest = new ComplianceOfficerRequest();
        officerRequest.setUserCode("OFFICER_RESET_01");
        officerRequest.setEmployeeId("EMP_OFFICER_RESET");
        officerRequest.setFirstName("Reset");
        officerRequest.setLastName("Officer");
        officerRequest.setEmail("officer_reset@hdfc.com");
        officerRequest.setPhoneNumber("9876543211");

        MvcResult createResult = mockMvc.perform(post("/api/v1/bank/admin/add-compliance-officer")
                        .header("Authorization", "Bearer " + bankAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(officerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String officerId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("userId").asText();

        MvcResult resetResult = mockMvc.perform(post("/api/v1/bank/admin/compliance-officers/" + officerId + "/reset-password")
                        .header("Authorization", "Bearer " + bankAdminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode resetResponse = objectMapper.readTree(resetResult.getResponse().getContentAsString());
        assertThat(resetResponse.get("mustResetPassword").asBoolean()).isTrue();
        assertThat(resetResponse.get("temporaryPassword").asText()).startsWith("TmpOff@");
    }

    @Test
    @DisplayName("BankAdmin can deactivate Compliance Officer and open cases are reassigned to Bank Admin")
    void bankAdminCanDeactivateComplianceOfficerAndReassignCases() throws Exception {
        ComplianceOfficerRequest officerRequest = new ComplianceOfficerRequest();
        officerRequest.setUserCode("OFFICER_DEACT_01");
        officerRequest.setEmployeeId("EMP_OFFICER_DEACT");
        officerRequest.setFirstName("Deact");
        officerRequest.setLastName("Officer");
        officerRequest.setEmail("officer_deact@hdfc.com");
        officerRequest.setPhoneNumber("9876543212");

        MvcResult createResult = mockMvc.perform(post("/api/v1/bank/admin/add-compliance-officer")
                        .header("Authorization", "Bearer " + bankAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(officerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String officerId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("userId").asText();

        Users coUser = userRepository.findById(java.util.UUID.fromString(officerId)).orElseThrow();
        Users bankAdminUser = userRepository.findByEmail("admin_mgmt@hdfc.com").orElseThrow();

        TenantContext.setCurrentTenant(testTenant.getSchemaName());
        com.tss.aml.entities.tenant.AmlCase testCase = com.tss.aml.entities.tenant.AmlCase.builder()
                .caseCode("CASE-DEACT-01")
                .createdBy(bankAdminUser)
                .assignedTo(coUser)
                .status(com.tss.aml.enums.CaseStatus.OPEN)
                .build();
        testCase = amlCaseRepository.save(testCase);
        TenantContext.clear();

        MvcResult deactResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/bank/admin/compliance-officers/" + officerId + "/deactivate")
                        .header("Authorization", "Bearer " + bankAdminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode deactResponse = objectMapper.readTree(deactResult.getResponse().getContentAsString());
        assertThat(deactResponse.get("isActive").asBoolean()).isFalse();

        TenantContext.setCurrentTenant(testTenant.getSchemaName());
        com.tss.aml.entities.tenant.AmlCase reassignedCase = amlCaseRepository.findById(testCase.getCaseId()).orElseThrow();
        assertThat(reassignedCase.getAssignedTo().getUserId()).isEqualTo(bankAdminUser.getUserId());
        TenantContext.clear();
    }
}
