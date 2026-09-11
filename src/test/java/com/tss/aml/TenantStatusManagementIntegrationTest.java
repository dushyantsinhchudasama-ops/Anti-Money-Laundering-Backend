package com.tss.aml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.aml.config.SystemAdminDataInitializer;
import com.tss.aml.dtos.auth.LoginRequest;
import com.tss.aml.dtos.tenant.UpdateTenantStatusRequest;
import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.entities.system.Tenant;
import com.tss.aml.entities.system.Users;
import com.tss.aml.enums.TenantStatus;
import com.tss.aml.enums.UserRole;
import com.tss.aml.repositories.SystemAdminRepository;
import com.tss.aml.repositories.TenantRepository;
import com.tss.aml.repositories.UserRepository;
import com.tss.aml.security.CustomUserDetails;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class TenantStatusManagementIntegrationTest {

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

    private String systemAdminToken;
    private Tenant testTenant;
    private Users bankAdminUser;

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

        SystemAdmin systemAdmin = systemAdminRepository.findAll().get(0);
        CustomUserDetails adminDetails = (CustomUserDetails) customUserDetailsService.loadUserByUsername(systemAdmin.getEmail());
        Authentication auth = new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities());
        systemAdminToken = jwtTokenProvider.generateToken(auth);

        testTenant = tenantRepository.findByTenantCode("STATUS_BANK")
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .tenantCode("STATUS_BANK")
                        .tenantName("Status Bank")
                        .displayName("Status Bank")
                        .schemaName("public")
                        .status(TenantStatus.ACTIVE)
                        .onboardedByAdmin(systemAdmin)
                        .build()));
        testTenant.setStatus(TenantStatus.ACTIVE);
        testTenant = tenantRepository.save(testTenant);

        bankAdminUser = userRepository.findByEmail("admin@statusbank.com")
                .orElseGet(() -> userRepository.save(Users.builder()
                        .tenant(testTenant)
                        .userCode("SB_ADM_01")
                        .role(UserRole.BANK_ADMIN)
                        .firstName("Bank")
                        .lastName("Admin")
                        .email("admin@statusbank.com")
                        .passwordHash(passwordEncoder.encode("Password@123"))
                        .isActive(true)
                        .mustResetPassword(false)
                        .build()));
        bankAdminUser.setPasswordHash(passwordEncoder.encode("Password@123"));
        userRepository.save(bankAdminUser);
    }

    @Test
    @DisplayName("A & B & C. SYSTEM_ADMIN can update tenant status ACTIVE -> SUSPENDED -> ACTIVE -> OFFBOARDED")
    void systemAdminCanUpdateStatus() throws Exception {
        UUID tenantId = testTenant.getTenantId();

        // ACTIVE -> SUSPENDED
        UpdateTenantStatusRequest reqSuspended = new UpdateTenantStatusRequest(TenantStatus.SUSPENDED);
        mockMvc.perform(patch("/api/v1/system/admin/tenants/" + tenantId + "/status")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqSuspended)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        assertThat(tenantRepository.findById(tenantId).get().getStatus()).isEqualTo(TenantStatus.SUSPENDED);

        // SUSPENDED -> ACTIVE
        UpdateTenantStatusRequest reqActive = new UpdateTenantStatusRequest(TenantStatus.ACTIVE);
        mockMvc.perform(patch("/api/v1/system/admin/tenants/" + tenantId + "/status")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqActive)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(tenantRepository.findById(tenantId).get().getStatus()).isEqualTo(TenantStatus.ACTIVE);

        // ACTIVE -> OFFBOARDED
        UpdateTenantStatusRequest reqOffboarded = new UpdateTenantStatusRequest(TenantStatus.OFFBOARDED);
        mockMvc.perform(patch("/api/v1/system/admin/tenants/" + tenantId + "/status")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqOffboarded)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFBOARDED"));

        assertThat(tenantRepository.findById(tenantId).get().getStatus()).isEqualTo(TenantStatus.OFFBOARDED);
    }

    @Test
    @DisplayName("D. Non-SystemAdmin (BANK_ADMIN) cannot update tenant status")
    void nonSystemAdminCannotUpdateStatus() throws Exception {
        CustomUserDetails bankAdminDetails = (CustomUserDetails) customUserDetailsService.loadUserByUsername("admin@statusbank.com");
        Authentication auth = new UsernamePasswordAuthenticationToken(bankAdminDetails, null, bankAdminDetails.getAuthorities());
        String bankAdminToken = jwtTokenProvider.generateToken(auth);

        UpdateTenantStatusRequest request = new UpdateTenantStatusRequest(TenantStatus.SUSPENDED);

        mockMvc.perform(patch("/api/v1/system/admin/tenants/" + testTenant.getTenantId() + "/status")
                        .header("Authorization", "Bearer " + bankAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("E. ACTIVE tenant login succeeds and returns valid JWT token")
    void activeTenantLoginSuccess() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("admin@statusbank.com");
        loginRequest.setPassword("Password@123");

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        Map<?, ?> map = objectMapper.readValue(json, Map.class);
        assertThat(map.get("accessToken")).isNotNull();
    }

    @Test
    @DisplayName("F & G. SUSPENDED tenant login fails with 401 and exact suspension message; no JWT issued")
    void suspendedTenantLoginFails() throws Exception {
        testTenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.save(testTenant);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("admin@statusbank.com");
        loginRequest.setPassword("Password@123");

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Institution suspended Please contact Admin!"))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertThat(json).doesNotContain("accessToken");
    }

    @Test
    @DisplayName("H & I. OFFBOARDED tenant login fails with exact invalid credentials behavior; no JWT issued")
    void offboardedTenantLoginFails() throws Exception {
        testTenant.setStatus(TenantStatus.OFFBOARDED);
        tenantRepository.save(testTenant);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("admin@statusbank.com");
        loginRequest.setPassword("Password@123");

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Bad credentials"))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertThat(json).doesNotContain("accessToken");
        assertThat(json).doesNotContain("OFFBOARDED");
        assertThat(json).doesNotContain("offboarded");
    }

    @Test
    @DisplayName("J. Token issued while ACTIVE stops working after tenant is SUSPENDED")
    void activeTokenFailsAfterSuspension() throws Exception {
        CustomUserDetails userDetails = (CustomUserDetails) customUserDetailsService.loadUserByUsername("admin@statusbank.com");
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        String token = jwtTokenProvider.generateToken(auth);

        // Verify token works while ACTIVE
        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Suspend tenant
        testTenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.save(testTenant);

        // Verify token is rejected after suspension
        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Institution suspended Please contact Admin!"));
    }

    @Test
    @DisplayName("K. Token issued while ACTIVE stops working after tenant is OFFBOARDED")
    void activeTokenFailsAfterOffboarding() throws Exception {
        CustomUserDetails userDetails = (CustomUserDetails) customUserDetailsService.loadUserByUsername("admin@statusbank.com");
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        String token = jwtTokenProvider.generateToken(auth);

        // Verify token works while ACTIVE
        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Offboard tenant
        testTenant.setStatus(TenantStatus.OFFBOARDED);
        tenantRepository.save(testTenant);

        // Verify token is rejected after offboarding
        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Bad credentials"));
    }
}
