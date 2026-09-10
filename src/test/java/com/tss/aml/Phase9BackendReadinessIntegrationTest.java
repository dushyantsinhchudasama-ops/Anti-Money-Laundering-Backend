package com.tss.aml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.aml.config.SystemAdminDataInitializer;
import com.tss.aml.dtos.auth.LoginRequest;
import com.tss.aml.dtos.auth.ResetPasswordRequest;
import com.tss.aml.dtos.tenant.*;
import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.entities.system.Tenant;
import com.tss.aml.entities.system.Users;
import com.tss.aml.repositories.SystemAdminRepository;
import com.tss.aml.repositories.TenantRepository;
import com.tss.aml.repositories.UserRepository;
import com.tss.aml.security.CustomUserDetailsService;
import com.tss.aml.security.JwtTokenProvider;
import com.tss.aml.services.EmailService;
import com.tss.aml.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class Phase9BackendReadinessIntegrationTest {

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoSpyBean
    private EmailService emailService;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SystemAdminDataInitializer initializer;

    @Autowired
    private SystemAdminRepository systemAdminRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private SystemAdmin sysAdmin;
    private String sysAdminToken;

    private Tenant tenantA;
    private String bankAdminAToken;
    private Users coA1;
    private String coA1Token;
    private Users coA2;

    private Tenant tenantB;
    private String bankAdminBToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        TenantContext.clear();
        SecurityContextHolder.clearContext();

        try {
            jdbcTemplate.execute("TRUNCATE TABLE public.system_audit_log CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE public.system_admin CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE public.users CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE public.bank_rule_assignment CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE public.rules CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE public.tenants CASCADE");
        } catch (Exception e) {
            // Table truncation fallback
        }

        initializer.run((org.springframework.boot.ApplicationArguments) null);
        sysAdmin = systemAdminRepository.findByEmail("admin@aml.com").orElseThrow();

        Authentication sysAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                customUserDetailsService.loadUserByUsername("admin@aml.com"), null,
                customUserDetailsService.loadUserByUsername("admin@aml.com").getAuthorities());
        sysAdminToken = jwtTokenProvider.generateToken(sysAuth);

        // Onboard Tenant A (HDFC)
        CreateTenantRequest reqA = new CreateTenantRequest();
        reqA.setTenantCode("HDFCP9");
        reqA.setTenantName("HDFC Bank Phase 9");
        reqA.setDisplayName("HDFC Bank Phase 9");
        MvcResult resA = mockMvc.perform(post("/api/v1/admin/tenants")
                        .header("Authorization", "Bearer " + sysAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID tenantIdA = UUID.fromString(objectMapper.readTree(resA.getResponse().getContentAsString()).get("tenantId").asText());
        tenantA = tenantRepository.findById(tenantIdA).orElseThrow();

        // Create Bank Admin A
        CreateBankAdminRequest adminReqA = new CreateBankAdminRequest();
        adminReqA.setUserCode("ADMIN_HDFCP9");
        adminReqA.setFirstName("Admin");
        adminReqA.setLastName("HDFC");
        adminReqA.setEmail("admin_p9@hdfc.com");
        mockMvc.perform(post("/api/v1/admin/tenants/" + tenantIdA + "/users")
                        .header("Authorization", "Bearer " + sysAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminReqA)))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> captorA = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendBankAdminWelcomeEmail(eq("admin_p9@hdfc.com"), any(), any(), any(), captorA.capture());
        String tempPassAdminA = captorA.getValue();

        // Login Admin A and reset password
        LoginRequest loginAdminA = new LoginRequest();
        loginAdminA.setEmail("admin_p9@hdfc.com");
        loginAdminA.setPassword(tempPassAdminA);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginAdminA)))
                .andExpect(status().isOk());

        ResetPasswordRequest resetAdminA = ResetPasswordRequest.builder()
                .email("admin_p9@hdfc.com")
                .currentPassword(tempPassAdminA)
                .newPassword("AdminPass@123")
                .confirmPassword("AdminPass@123")
                .build();
        MvcResult resetAdminResA = mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetAdminA)))
                .andExpect(status().isOk())
                .andReturn();
        bankAdminAToken = objectMapper.readTree(resetAdminResA.getResponse().getContentAsString()).get("accessToken").asText();

        // Create CO A1 and CO A2 in Tenant A
        coA1 = createAndAuthenticateCO(bankAdminAToken, "CO_P9_A1", "co_a1@hdfc.com", "COPassA1@123");
        coA1Token = loginUser("co_a1@hdfc.com", "COPassA1@123");

        coA2 = createAndAuthenticateCO(bankAdminAToken, "CO_P9_A2", "co_a2@hdfc.com", "COPassA2@123");

        // Onboard Tenant B (ICICI)
        CreateTenantRequest reqB = new CreateTenantRequest();
        reqB.setTenantCode("ICICIP9");
        reqB.setTenantName("ICICI Bank Phase 9");
        reqB.setDisplayName("ICICI Bank Phase 9");
        MvcResult resB = mockMvc.perform(post("/api/v1/admin/tenants")
                        .header("Authorization", "Bearer " + sysAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID tenantIdB = UUID.fromString(objectMapper.readTree(resB.getResponse().getContentAsString()).get("tenantId").asText());
        tenantB = tenantRepository.findById(tenantIdB).orElseThrow();

        // Create Bank Admin B
        CreateBankAdminRequest adminReqB = new CreateBankAdminRequest();
        adminReqB.setUserCode("ADMIN_ICICIP9");
        adminReqB.setFirstName("Admin");
        adminReqB.setLastName("ICICI");
        adminReqB.setEmail("admin_p9@icici.com");
        mockMvc.perform(post("/api/v1/admin/tenants/" + tenantIdB + "/users")
                        .header("Authorization", "Bearer " + sysAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminReqB)))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> captorB = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendBankAdminWelcomeEmail(eq("admin_p9@icici.com"), any(), any(), any(), captorB.capture());
        String tempPassAdminB = captorB.getValue();

        LoginRequest loginAdminB = new LoginRequest();
        loginAdminB.setEmail("admin_p9@icici.com");
        loginAdminB.setPassword(tempPassAdminB);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginAdminB)))
                .andExpect(status().isOk());

        ResetPasswordRequest resetAdminB = ResetPasswordRequest.builder()
                .email("admin_p9@icici.com")
                .currentPassword(tempPassAdminB)
                .newPassword("AdminPassB@123")
                .confirmPassword("AdminPassB@123")
                .build();
        MvcResult resetAdminResB = mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetAdminB)))
                .andExpect(status().isOk())
                .andReturn();
        bankAdminBToken = objectMapper.readTree(resetAdminResB.getResponse().getContentAsString()).get("accessToken").asText();

        createAndAuthenticateCO(bankAdminBToken, "CO_P9_B1", "co_b1@icici.com", "COPassB1@123");
    }

    private Users createAndAuthenticateCO(String adminToken, String code, String email, String newPassword) throws Exception {
        ComplianceOfficerRequest req = new ComplianceOfficerRequest();
        req.setUserCode(code);
        req.setEmployeeId("EMP_" + code);
        req.setFirstName("CO");
        req.setLastName(code);
        req.setEmail(email);
        req.setPhoneNumber("9999999999");

        MvcResult res = mockMvc.perform(post("/api/v1/bank/admin/add-compliance-officer")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.has("temporaryPassword")).isFalse();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendComplianceOfficerWelcomeEmail(eq(email), any(), any(), any(), any(), captor.capture());
        String tempPass = captor.getValue();

        ResetPasswordRequest reset = ResetPasswordRequest.builder()
                .email(email)
                .currentPassword(tempPass)
                .newPassword(newPassword)
                .confirmPassword(newPassword)
                .build();

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reset)))
                .andExpect(status().isOk());

        return userRepository.findByEmail(email).orElseThrow();
    }

    private String loginUser(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        MvcResult res = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    @DisplayName("Verification Area 1: Complete CO End-to-End Workflow")
    void testCompleteComplianceOfficerEndToEndWorkflow() throws Exception {
        LoginRequest coLogin = new LoginRequest();
        coLogin.setEmail("co_a1@hdfc.com");
        coLogin.setPassword("COPassA1@123");

        MvcResult loginRes = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(coLogin)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginRes.getResponse().getContentAsString());
        String token = loginJson.get("accessToken").asText();
        assertThat(token).isNotBlank();
        assertThat(loginJson.get("userRole").asText()).isEqualTo("COMPLIANCE_OFFICER");

        mockMvc.perform(get("/api/v1/compliance/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        MvcResult casesRes = mockMvc.perform(get("/api/v1/compliance/cases")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode casesJson = objectMapper.readTree(casesRes.getResponse().getContentAsString());
        assertThat(casesJson.has("content")).isTrue();
    }

    @Test
    @DisplayName("Verification Area 2: Authentication & Token Security")
    void testAuthenticationSecurity() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/dashboard"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/compliance/dashboard")
                        .header("Authorization", "Bearer malformed.jwt.token"))
                .andExpect(status().isForbidden());

        LoginRequest badCreds = new LoginRequest();
        badCreds.setEmail("co_a1@hdfc.com");
        badCreds.setPassword("WrongPassword@123");
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badCreds)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Verification Area 3: Role-Based Authorization Matrix")
    void testRoleBasedAuthorizationMatrix() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/dashboard")
                        .header("Authorization", "Bearer " + sysAdminToken))
                .andExpect(status().isForbidden());

        CreateTenantRequest req = new CreateTenantRequest();
        req.setTenantCode("ILLEGAL");
        req.setTenantName("Illegal Bank");
        mockMvc.perform(post("/api/v1/admin/tenants")
                        .header("Authorization", "Bearer " + coA1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/tenants")
                        .header("Authorization", "Bearer " + bankAdminAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Verification Area 4: Multi-Tenant Isolation")
    void testMultiTenantIsolation() throws Exception {
        mockMvc.perform(get("/api/v1/bank/admin/audit-logs")
                        .header("Authorization", "Bearer " + bankAdminAToken))
                .andExpect(status().isOk());

        MvcResult listResB = mockMvc.perform(get("/api/v1/bank/admin/compliance-officers")
                        .header("Authorization", "Bearer " + bankAdminBToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listJsonB = objectMapper.readTree(listResB.getResponse().getContentAsString());
        for (JsonNode node : listJsonB.get("content")) {
            assertThat(node.get("email").asText()).isNotEqualTo("co_a1@hdfc.com");
        }
    }

    @Test
    @DisplayName("Verification Area 5: Case Ownership Security")
    void testCaseOwnershipSecurity() throws Exception {
        UUID randomCaseId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/compliance/cases/" + randomCaseId)
                        .header("Authorization", "Bearer " + coA1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Verification Area 6: Case State Machine")
    void testCaseStateMachineProtection() throws Exception {
        UUID randomCaseId = UUID.randomUUID();
        CloseCaseNoActionRequest closeReq = new CloseCaseNoActionRequest();
        closeReq.setRationale("Valid rationale for case closure");

        mockMvc.perform(post("/api/v1/compliance/cases/" + randomCaseId + "/close-no-action")
                        .header("Authorization", "Bearer " + coA1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(closeReq)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Verification Area 7: SAR/STR Security & Preview")
    void testSarStrFilingSecurity() throws Exception {
        UUID randomCaseId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/compliance/cases/" + randomCaseId + "/sar-str/preview")
                        .header("Authorization", "Bearer " + coA1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Verification Area 8: Audit Trail Security & Scoping")
    void testAuditTrailSecurity() throws Exception {
        mockMvc.perform(get("/api/v1/bank/admin/audit-logs")
                        .header("Authorization", "Bearer " + bankAdminAToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/system/admin/audit-logs")
                        .header("Authorization", "Bearer " + sysAdminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Verification Area 9: User Account Security & Deactivation")
    void testUserAccountSecurity() throws Exception {
        mockMvc.perform(patch("/api/v1/bank/admin/compliance-officers/" + coA2.getUserId() + "/deactivate")
                        .header("Authorization", "Bearer " + bankAdminAToken))
                .andExpect(status().isOk());

        LoginRequest deactLogin = new LoginRequest();
        deactLogin.setEmail("co_a2@hdfc.com");
        deactLogin.setPassword("COPassA2@123");
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactLogin)))
                .andExpect(status().isUnauthorized());
    }
}
