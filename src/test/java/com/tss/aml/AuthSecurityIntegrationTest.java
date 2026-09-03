package com.tss.aml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.aml.config.SystemAdminDataInitializer;
import com.tss.aml.dtos.auth.LoginRequest;
import com.tss.aml.entities.system.Tenant;
import com.tss.aml.entities.system.Users;
import com.tss.aml.enums.TenantStatus;
import com.tss.aml.enums.UserRole;
import com.tss.aml.repositories.SystemAdminRepository;
import com.tss.aml.repositories.TenantRepository;
import com.tss.aml.repositories.UserRepository;
import com.tss.aml.security.CustomUserDetails;
import com.tss.aml.security.CustomUserDetailsService;
import com.tss.aml.security.JwtAuthenticationFilter;
import com.tss.aml.security.JwtTokenProvider;
import com.tss.aml.tenant.TenantContext;
import com.tss.aml.tenant.TenantService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuthSecurityIntegrationTest {

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
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        TenantContext.clear();
        SecurityContextHolder.clearContext();

        // Ensure default SystemAdmin exists
        if (systemAdminRepository.count() == 0) {
            initializer.run(null);
        }
    }

    @Test
    @DisplayName("A. SystemAdmin login produces HTTP 200, JWT without tenantId, and ROLE_SYSTEM_ADMIN authority")
    void systemAdminLoginSuccess() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("admin@aml.com");
        loginRequest.setPassword("Admin@123");

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String jsonResponse = result.getResponse().getContentAsString();
        Map<?, ?> responseMap = objectMapper.readValue(jsonResponse, Map.class);
        String token = (String) responseMap.get("accessToken");

        assertThat(token).isNotNull();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getTenantId(token)).isNull();

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("admin@aml.com");
        assertThat(userDetails).isInstanceOf(CustomUserDetails.class);
        CustomUserDetails customUserDetails = (CustomUserDetails) userDetails;
        assertThat(customUserDetails.getTenantId()).isNull();
        assertThat(customUserDetails.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_SYSTEM_ADMIN");
    }

    @Test
    @DisplayName("B. SystemAdmin JWT on protected endpoint authenticates without tenant schema lookup or NPE")
    void systemAdminJwtOnProtectedEndpoint() throws Exception {
        UserDetails adminDetails = customUserDetailsService.loadUserByUsername("admin@aml.com");
        Authentication auth = new UsernamePasswordAuthenticationToken(
                adminDetails, null, adminDetails.getAuthorities()
        );
        String token = jwtTokenProvider.generateToken(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/tenants");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Authentication> authInChain = new AtomicReference<>();
        FilterChain filterChain = (req, res) -> authInChain.set(SecurityContextHolder.getContext().getAuthentication());

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(authInChain.get()).isNotNull();
        assertThat(authInChain.get().getPrincipal()).isInstanceOf(CustomUserDetails.class);
        CustomUserDetails user = (CustomUserDetails) authInChain.get().getPrincipal();
        assertThat(user.getUsername()).isEqualTo("admin@aml.com");
        assertThat(user.getTenantId()).isNull();
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("C. Tenant user JWT extracts tenantId, resolves schemaName, and sets TenantContext")
    void tenantUserJwtSetsTenantContext() throws Exception {
        Tenant tenant = tenantRepository.findByTenantCode("HDFC")
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .tenantCode("HDFC")
                        .tenantName("HDFC Bank")
                        .displayName("HDFC Bank")
                        .schemaName("tenant_hdfc")
                        .status(TenantStatus.ACTIVE)
                        .onboardedByAdmin(systemAdminRepository.findAll().get(0))
                        .build()));


        Users user = userRepository.findByEmail("user@hdfc.com")
                .orElseGet(() -> userRepository.save(Users.builder()
                        .tenant(tenant)
                        .userCode("USR001")
                        .role(UserRole.BANK_ADMIN)
                        .firstName("Bank")
                        .lastName("Admin")
                        .email("user@hdfc.com")
                        .passwordHash("password")
                        .isActive(true)
                        .mustResetPassword(false)
                        .build()));

        CustomUserDetails userDetails = (CustomUserDetails) customUserDetailsService.loadUserByUsername("user@hdfc.com");

        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );

        String token = jwtTokenProvider.generateToken(auth);
        UUID tenantId = tenant.getTenantId();
        String schemaName = "tenant_hdfc";

        when(tenantService.getSchemaName(tenantId)).thenReturn(schemaName);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cases");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> tenantSeen = new AtomicReference<>();
        FilterChain chain = (req, res) -> tenantSeen.set(TenantContext.getCurrentTenant());

        jwtAuthenticationFilter.doFilter(request, response, chain);

        assertThat(tenantSeen.get()).isEqualTo("tenant_hdfc");
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("D & E. POST /auth/login works without Authorization header and succeeds with invalid/inherited Authorization header")
    void loginWithOrWithoutAuthHeader() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("admin@aml.com");
        loginRequest.setPassword("Admin@123");

        // Request WITHOUT Authorization header
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());

        // Request WITH invalid / inherited Authorization header
        mockMvc.perform(post("/auth/login")
                        .header("Authorization", "Bearer invalid-or-inherited-postman-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("F & G. Protected endpoints reject requests without JWT or with invalid JWT")
    void protectedEndpointRejectsUnauthenticated() throws Exception {
        // Without JWT
        mockMvc.perform(post("/api/v1/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        // With Invalid JWT
        mockMvc.perform(post("/api/v1/admin/tenants")
                        .header("Authorization", "Bearer invalid-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
