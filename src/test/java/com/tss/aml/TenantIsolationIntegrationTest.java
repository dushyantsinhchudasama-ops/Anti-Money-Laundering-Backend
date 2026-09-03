package com.tss.aml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.aml.config.SystemAdminDataInitializer;
import com.tss.aml.dtos.tenant.CreateBankAdminRequest;
import com.tss.aml.dtos.tenant.CreateTenantRequest;
import com.tss.aml.dtos.tenant.CreateTenantResponse;
import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.entities.system.Tenant;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class TenantIsolationIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    private SystemAdmin systemAdmin;
    private String systemAdminToken;

    private Tenant hdfcTenant;
    private Tenant iciciTenant;

    private String hdfcAdminToken;
    private String iciciAdminToken;

    @BeforeEach
    void setUp() throws Exception {
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

        // Set system admin authentication for onboarding
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 1. Ensure HDFC Tenant & Schema exist
        if (!tenantRepository.existsByTenantCode("HDFC_ISO")) {
            CreateTenantResponse hdfcResp = tenantService.onboardTenant(CreateTenantRequest.builder()
                    .tenantCode("HDFC_ISO")
                    .tenantName("HDFC Isolation Bank")
                    .displayName("HDFC Isolation")
                    .build());
            hdfcTenant = tenantRepository.findById(hdfcResp.getTenantId()).orElseThrow();
        } else {
            hdfcTenant = tenantRepository.findByTenantCode("HDFC_ISO").orElseThrow();
        }

        // 2. Ensure ICICI Tenant & Schema exist
        if (!tenantRepository.existsByTenantCode("ICICI_ISO")) {
            CreateTenantResponse iciciResp = tenantService.onboardTenant(CreateTenantRequest.builder()
                    .tenantCode("ICICI_ISO")
                    .tenantName("ICICI Isolation Bank")
                    .displayName("ICICI Isolation")
                    .build());
            iciciTenant = tenantRepository.findById(iciciResp.getTenantId()).orElseThrow();
        } else {
            iciciTenant = tenantRepository.findByTenantCode("ICICI_ISO").orElseThrow();
        }

        // Clean up stale isolation test users to guarantee valid link to current tenant
        userRepository.findByEmail("admin_iso@hdfc.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("admin_iso@icici.com").ifPresent(userRepository::delete);

        // 3. Ensure HDFC Bank Admin user exists
        String hdfcEmail = "admin_iso@hdfc.com";
        CreateBankAdminRequest hdfcReq = new CreateBankAdminRequest();
        hdfcReq.setUserCode("HDFC_ISO_ADMIN");
        hdfcReq.setFirstName("HDFC");
        hdfcReq.setLastName("Admin");
        hdfcReq.setEmail(hdfcEmail);
        tenantService.createBankAdmin(hdfcTenant.getTenantId(), hdfcReq);

        UserDetails hdfcUserDetails = customUserDetailsService.loadUserByUsername(hdfcEmail);
        Authentication hdfcAuth = new UsernamePasswordAuthenticationToken(
                hdfcUserDetails, null, hdfcUserDetails.getAuthorities()
        );
        hdfcAdminToken = jwtTokenProvider.generateToken(hdfcAuth);

        // 4. Ensure ICICI Bank Admin user exists
        String iciciEmail = "admin_iso@icici.com";
        CreateBankAdminRequest iciciReq = new CreateBankAdminRequest();
        iciciReq.setUserCode("ICICI_ISO_ADMIN");
        iciciReq.setFirstName("ICICI");
        iciciReq.setLastName("Admin");
        iciciReq.setEmail(iciciEmail);
        tenantService.createBankAdmin(iciciTenant.getTenantId(), iciciReq);

        UserDetails iciciUserDetails = customUserDetailsService.loadUserByUsername(iciciEmail);
        Authentication iciciAuth = new UsernamePasswordAuthenticationToken(
                iciciUserDetails, null, iciciUserDetails.getAuthorities()
        );
        iciciAdminToken = jwtTokenProvider.generateToken(iciciAuth);

        SecurityContextHolder.clearContext();

        // 5. Clean up old test account records in tenant schemas
        jdbcTemplate.execute("DELETE FROM " + hdfcTenant.getSchemaName() + ".account WHERE account_number LIKE '%-TEST-%'");
        jdbcTemplate.execute("DELETE FROM " + iciciTenant.getSchemaName() + ".account WHERE account_number LIKE '%-TEST-%'");

        // 6. Insert distinguishable test data in HDFC schema (tenant_hdfc_iso.account)
        jdbcTemplate.execute(String.format(
                "INSERT INTO %s.account (account_id, account_number, account_holder_name, account_type, country_code, created_at, updated_at) " +
                        "VALUES ('%s', 'HDFC-TEST-001', 'HDFC Test User', 'SAVINGS', 'IN', NOW(), NOW())",
                hdfcTenant.getSchemaName(), UUID.randomUUID()
        ));

        // 7. Insert distinguishable test data in ICICI schema (tenant_icici_iso.account)
        jdbcTemplate.execute(String.format(
                "INSERT INTO %s.account (account_id, account_number, account_holder_name, account_type, country_code, created_at, updated_at) " +
                        "VALUES ('%s', 'ICICI-TEST-001', 'ICICI Test User', 'CURRENT', 'IN', NOW(), NOW())",
                iciciTenant.getSchemaName(), UUID.randomUUID()
        ));
    }

    @Test
    @DisplayName("PART 3 & PART 8A. HDFC JWT accesses tenant_hdfc schema and retrieves ONLY HDFC-TEST-001")
    void hdfcAccessTest() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + hdfcAdminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.isArray()).isTrue();

        List<String> accountNumbers = json.findValuesAsText("accountNumber");
        assertThat(accountNumbers).contains("HDFC-TEST-001");
        assertThat(accountNumbers).doesNotContain("ICICI-TEST-001");
    }

    @Test
    @DisplayName("PART 4 & PART 8B. ICICI JWT accesses tenant_icici schema and retrieves ONLY ICICI-TEST-001")
    void iciciAccessTest() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + iciciAdminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.isArray()).isTrue();

        List<String> accountNumbers = json.findValuesAsText("accountNumber");
        assertThat(accountNumbers).contains("ICICI-TEST-001");
        assertThat(accountNumbers).doesNotContain("HDFC-TEST-001");
    }

    @Test
    @DisplayName("PART 5 & PART 8G. Tenant override attempt via header X-Tenant-ID / X-Tenant-Schema is ignored")
    void tenantOverrideAttemptIsIgnored() throws Exception {
        // Attempting to override tenant schema using headers while supplying HDFC JWT
        MvcResult result = mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + hdfcAdminToken)
                        .header("X-Tenant-ID", iciciTenant.getTenantId().toString())
                        .header("X-Tenant-Schema", iciciTenant.getSchemaName()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        List<String> accountNumbers = json.findValuesAsText("accountNumber");

        // JWT remains authoritative: returned data MUST still belong to HDFC
        assertThat(accountNumbers).contains("HDFC-TEST-001");
        assertThat(accountNumbers).doesNotContain("ICICI-TEST-001");
    }

    @Test
    @DisplayName("PART 6 & PART 8C/D. Direct PostgreSQL schema verification proves physical data separation")
    void directPostgresSchemaVerification() {
        Integer hdfcCountInHdfc = jdbcTemplate.queryForObject(
                String.format("SELECT COUNT(*) FROM %s.account WHERE account_number = 'HDFC-TEST-001'", hdfcTenant.getSchemaName()),
                Integer.class
        );
        Integer iciciCountInHdfc = jdbcTemplate.queryForObject(
                String.format("SELECT COUNT(*) FROM %s.account WHERE account_number = 'ICICI-TEST-001'", hdfcTenant.getSchemaName()),
                Integer.class
        );

        Integer iciciCountInIcici = jdbcTemplate.queryForObject(
                String.format("SELECT COUNT(*) FROM %s.account WHERE account_number = 'ICICI-TEST-001'", iciciTenant.getSchemaName()),
                Integer.class
        );
        Integer hdfcCountInIcici = jdbcTemplate.queryForObject(
                String.format("SELECT COUNT(*) FROM %s.account WHERE account_number = 'HDFC-TEST-001'", iciciTenant.getSchemaName()),
                Integer.class
        );

        assertThat(hdfcCountInHdfc).isEqualTo(1);
        assertThat(iciciCountInHdfc).isEqualTo(0);
        assertThat(iciciCountInIcici).isEqualTo(1);
        assertThat(hdfcCountInIcici).isEqualTo(0);
    }

    @Test
    @DisplayName("PART 7 & PART 8H. Sequential requests switch TenantContext cleanly and clear it after request completion")
    void sequentialRequestsDoNotLeakTenantContext() throws Exception {
        // Request 1: HDFC JWT
        MvcResult res1 = mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + hdfcAdminToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(res1.getResponse().getContentAsString()).findValuesAsText("accountNumber"))
                .contains("HDFC-TEST-001");

        // Verify context cleared after request 1
        assertThat(TenantContext.getCurrentTenant()).isNull();

        // Request 2: ICICI JWT
        MvcResult res2 = mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + iciciAdminToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(res2.getResponse().getContentAsString()).findValuesAsText("accountNumber"))
                .contains("ICICI-TEST-001");

        // Verify context cleared after request 2
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("PART 8E/F. Missing or Invalid JWT is rejected")
    void missingOrInvalidJwtIsRejected() throws Exception {
        // Missing JWT
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isForbidden());

        // Invalid JWT
        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PART 8I. SystemAdmin JWT preserves system-level context (tenantId = null)")
    void systemAdminPreservesSystemContext() throws Exception {
        assertThat(jwtTokenProvider.getTenantId(systemAdminToken)).isNull();

        // SystemAdmin request does not set a tenant schema in TenantContext
        mockMvc.perform(get("/api/v1/admin/tenants")
                        .header("Authorization", "Bearer " + systemAdminToken))
                .andExpect(status().isOk());

        assertThat(TenantContext.getCurrentTenant()).isNull();
    }
}
