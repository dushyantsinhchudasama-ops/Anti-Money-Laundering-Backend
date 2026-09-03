package com.tss.aml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.aml.config.SystemAdminDataInitializer;
import com.tss.aml.dtos.auth.LoginRequest;
import com.tss.aml.dtos.tenant.CreateBankAdminRequest;
import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.entities.system.Tenant;
import com.tss.aml.entities.system.Users;
import com.tss.aml.enums.TenantStatus;
import com.tss.aml.enums.UserRole;
import com.tss.aml.repositories.SystemAdminRepository;
import com.tss.aml.repositories.TenantRepository;
import com.tss.aml.repositories.UserRepository;
import com.tss.aml.security.CustomUserDetailsService;
import com.tss.aml.security.JwtTokenProvider;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class BankAdminCreationIntegrationTest {

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
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private SystemAdmin systemAdmin;
    private String systemAdminToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        TenantContext.clear();
        SecurityContextHolder.clearContext();

        if (systemAdminRepository.count() == 0) {
            initializer.run(null);
        }

        systemAdmin = systemAdminRepository.findAll().get(0);
        UserDetails adminDetails = customUserDetailsService.loadUserByUsername(systemAdmin.getEmail());
        Authentication auth = new UsernamePasswordAuthenticationToken(
                adminDetails, null, adminDetails.getAuthorities()
        );
        systemAdminToken = jwtTokenProvider.generateToken(auth);
    }

    private Tenant createTestTenant(String code, String name, String schema, TenantStatus status) {
        return tenantRepository.findByTenantCode(code)
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .tenantCode(code)
                        .tenantName(name)
                        .displayName(name)
                        .schemaName(schema)
                        .status(status)
                        .onboardedByAdmin(systemAdmin)
                        .build()));
    }

    @Test
    @DisplayName("A, B, H, I. SystemAdmin can create HDFC Bank Admin and DB row is correctly configured")
    void createHdfcBankAdminSuccess() throws Exception {
        Tenant hdfcTenant = createTestTenant("HDFC", "HDFC Bank", "tenant_hdfc", TenantStatus.ACTIVE);

        CreateBankAdminRequest request = new CreateBankAdminRequest();
        request.setUserCode("HDFC_ADMIN");
        request.setEmployeeId("EMP001");
        request.setFirstName("HDFC");
        request.setLastName("Admin");
        request.setPhoneNumber("9876543210");
        request.setEmail("admin@hdfc.com");

        MvcResult result = mockMvc.perform(post("/api/v1/admin/tenants/" + hdfcTenant.getTenantId() + "/users")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(response.get("userId")).isNotNull();
        assertThat(response.get("userCode").asText()).isEqualTo("HDFC_ADMIN");
        assertThat(UUID.fromString(response.get("tenantId").asText())).isEqualTo(hdfcTenant.getTenantId());
        assertThat(response.get("email").asText()).isEqualTo("admin@hdfc.com");
        assertThat(response.get("role").asText()).isEqualTo("BANK_ADMIN");
        assertThat(response.get("isActive").asBoolean()).isTrue();
        assertThat(response.get("mustResetPassword").asBoolean()).isTrue();
        String tempPassword = response.get("temporaryPassword").asText();
        assertThat(tempPassword).isNotNull().startsWith("TmpAdmin@");

        // B. Verify database row in public.users
        UUID createdUserId = UUID.fromString(response.get("userId").asText());
        Users userInDb = userRepository.findById(createdUserId).orElseThrow();
        assertThat(userInDb.getTenant().getTenantId()).isEqualTo(hdfcTenant.getTenantId());
        assertThat(userInDb.getRole()).isEqualTo(UserRole.BANK_ADMIN);
        assertThat(userInDb.getEmail()).isEqualTo("admin@hdfc.com");
        assertThat(userInDb.getIsActive()).isTrue();
        assertThat(userInDb.getMustResetPassword()).isTrue();

        // H. BCrypt hashed password check
        assertThat(userInDb.getPasswordHash()).startsWith("$2");
        assertThat(userInDb.getPasswordHash()).isNotEqualTo(tempPassword);
        assertThat(passwordEncoder.matches(tempPassword, userInDb.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("C. SystemAdmin can create ICICI Bank Admin with distinct tenant_id")
    void createIciciBankAdminSuccess() throws Exception {
        Tenant iciciTenant = createTestTenant("ICICI", "ICICI Bank", "tenant_icici", TenantStatus.ACTIVE);

        CreateBankAdminRequest request = new CreateBankAdminRequest();
        request.setUserCode("ICICI_ADMIN");
        request.setEmployeeId("EMP002");
        request.setFirstName("ICICI");
        request.setLastName("Admin");
        request.setEmail("admin@icici.com");

        MvcResult result = mockMvc.perform(post("/api/v1/admin/tenants/" + iciciTenant.getTenantId() + "/users")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(UUID.fromString(response.get("tenantId").asText())).isEqualTo(iciciTenant.getTenantId());

        UUID createdUserId = UUID.fromString(response.get("userId").asText());
        Users userInDb = userRepository.findById(createdUserId).orElseThrow();
        assertThat(userInDb.getTenant().getTenantId()).isEqualTo(iciciTenant.getTenantId());
        assertThat(userInDb.getEmail()).isEqualTo("admin@icici.com");
    }

    @Test
    @DisplayName("D. Non-SystemAdmin cannot create Bank Admin users")
    void nonSystemAdminCannotCreateBankAdmin() throws Exception {
        Tenant tenant = createTestTenant("AXIS", "Axis Bank", "tenant_axis", TenantStatus.ACTIVE);

        CreateBankAdminRequest request = new CreateBankAdminRequest();
        request.setUserCode("AXIS_ADMIN");
        request.setFirstName("Axis");
        request.setLastName("Admin");
        request.setEmail("admin@axis.com");

        // Unauthenticated request
        mockMvc.perform(post("/api/v1/admin/tenants/" + tenant.getTenantId() + "/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("E. Cannot create a user for a non-existent tenant")
    void nonExistentTenantFails() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        CreateBankAdminRequest request = new CreateBankAdminRequest();
        request.setUserCode("RANDOM_ADMIN");
        request.setFirstName("Random");
        request.setLastName("Admin");
        request.setEmail("admin@random.com");

        mockMvc.perform(post("/api/v1/admin/tenants/" + nonExistentId + "/users")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("F. Cannot create a user for an inactive tenant")
    void inactiveTenantFails() throws Exception {
        Tenant inactiveTenant = createTestTenant("INACTIVE", "Inactive Bank", "tenant_inactive", TenantStatus.ONBOARDING);

        CreateBankAdminRequest request = new CreateBankAdminRequest();
        request.setUserCode("INACTIVE_ADMIN");
        request.setFirstName("Inactive");
        request.setLastName("Admin");
        request.setEmail("admin@inactive.com");

        mockMvc.perform(post("/api/v1/admin/tenants/" + inactiveTenant.getTenantId() + "/users")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("G. Duplicate email/userCode is rejected")
    void duplicateEmailOrUserCodeRejected() throws Exception {
        Tenant tenant = createTestTenant("KOTAK", "Kotak Bank", "tenant_kotak", TenantStatus.ACTIVE);

        CreateBankAdminRequest request1 = new CreateBankAdminRequest();
        request1.setUserCode("KOTAK_ADMIN");
        request1.setFirstName("Kotak");
        request1.setLastName("Admin");
        request1.setEmail("admin@kotak.com");

        mockMvc.perform(post("/api/v1/admin/tenants/" + tenant.getTenantId() + "/users")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Duplicate email
        CreateBankAdminRequest requestDuplicateEmail = new CreateBankAdminRequest();
        requestDuplicateEmail.setUserCode("KOTAK_ADMIN_2");
        requestDuplicateEmail.setFirstName("Kotak");
        requestDuplicateEmail.setLastName("Admin2");
        requestDuplicateEmail.setEmail("admin@kotak.com");

        mockMvc.perform(post("/api/v1/admin/tenants/" + tenant.getTenantId() + "/users")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDuplicateEmail)))
                .andExpect(status().isBadRequest());

        // Duplicate userCode
        CreateBankAdminRequest requestDuplicateCode = new CreateBankAdminRequest();
        requestDuplicateCode.setUserCode("KOTAK_ADMIN");
        requestDuplicateCode.setFirstName("Kotak");
        requestDuplicateCode.setLastName("Admin3");
        requestDuplicateCode.setEmail("admin3@kotak.com");

        mockMvc.perform(post("/api/v1/admin/tenants/" + tenant.getTenantId() + "/users")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDuplicateCode)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Requirement 9. Created Bank Admin can authenticate with temporary password and receive tenant-scoped JWT")
    void bankAdminCanAuthenticateWithTemporaryPassword() throws Exception {
        Tenant sbiTenant = createTestTenant("SBI", "State Bank of India", "tenant_sbi", TenantStatus.ACTIVE);

        CreateBankAdminRequest request = new CreateBankAdminRequest();
        request.setUserCode("SBI_ADMIN");
        request.setFirstName("SBI");
        request.setLastName("Admin");
        request.setEmail("admin@sbi.com");

        MvcResult createResult = mockMvc.perform(post("/api/v1/admin/tenants/" + sbiTenant.getTenantId() + "/users")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());

        String tempPassword = createResponse.get("temporaryPassword").asText();
        assertThat(tempPassword).isNotNull();

        // Authenticate with generated temporary password
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("admin@sbi.com");
        loginRequest.setPassword(tempPassword);

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String jsonResponse = loginResult.getResponse().getContentAsString();
        Map<?, ?> responseMap = objectMapper.readValue(jsonResponse, Map.class);
        String token = (String) responseMap.get("accessToken");

        assertThat(token).isNotNull();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getTenantId(token)).isEqualTo(sbiTenant.getTenantId());
        assertThat(jwtTokenProvider.getUsername(token)).isEqualTo("admin@sbi.com");
    }
}
