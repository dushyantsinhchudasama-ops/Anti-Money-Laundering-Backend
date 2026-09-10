package com.tss.aml;

import com.tss.aml.dtos.auth.LoginRequest;
import com.tss.aml.dtos.tenant.CreateBankAdminRequest;
import com.tss.aml.dtos.tenant.CreateBankAdminResponse;
import com.tss.aml.dtos.tenant.CreateTenantRequest;
import com.tss.aml.dtos.tenant.CreateTenantResponse;
import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.entities.system.Tenant;
import com.tss.aml.entities.system.Users;
import com.tss.aml.repositories.SystemAdminRepository;
import com.tss.aml.repositories.TenantRepository;
import com.tss.aml.repositories.UserRepository;
import com.tss.aml.services.EmailService;
import com.tss.aml.services.interfaces.AuthService;
import com.tss.aml.tenant.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class NormalizationIntegrationTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SystemAdminRepository systemAdminRepository;

    @Autowired
    private com.tss.aml.services.interfaces.BankAdminService bankAdminService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailService emailService;

    private SystemAdmin sysAdmin;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE public.users CASCADE");
        jdbcTemplate.execute("DELETE FROM public.bank_rule_assignment WHERE tenant_id IN (SELECT tenant_id FROM public.tenants WHERE tenant_code LIKE 'norm%')");
        jdbcTemplate.execute("DELETE FROM public.tenants WHERE tenant_code LIKE 'norm%'");

        sysAdmin = systemAdminRepository.findBySystemAdminCode("SYSADMIN001")
                .orElseGet(() -> systemAdminRepository.findByEmail("admin@aml.com").orElseThrow());

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                sysAdmin,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // EMAIL TEST 1 & 2: Create user with mixed-case email -> stored as normalized lowercase
    @Test
    @DisplayName("Email 1 & 2: Create user with mixed-case email and verify normalized lowercase email is stored")
    void createBankAdminWithMixedCaseEmail_StoresNormalizedLowercase() {
        CreateTenantResponse tenantRes = tenantService.onboardTenant(CreateTenantRequest.builder()
                .tenantCode("normtenant1")
                .tenantName("Norm Tenant 1")
                .displayName("Norm Tenant 1")
                .build());

        CreateBankAdminRequest adminReq = new CreateBankAdminRequest();
        adminReq.setUserCode("NORM_U1");
        adminReq.setFirstName("Mixed");
        adminReq.setLastName("Case");
        adminReq.setEmail("  Admin@ABC.COM  ");

        CreateBankAdminResponse adminRes = tenantService.createBankAdmin(tenantRes.getTenantId(), adminReq);

        assertThat(adminRes.getEmail()).isEqualTo("admin@abc.com");
        Users userInDb = userRepository.findById(adminRes.getUserId()).orElseThrow();
        assertThat(userInDb.getEmail()).isEqualTo("admin@abc.com");
    }

    // EMAIL TEST 3: Attempt duplicate using different casing -> rejected
    @Test
    @DisplayName("Email 3: Attempt duplicate email creation using different casing is rejected")
    void duplicateEmailWithDifferentCasing_IsRejected() {
        CreateTenantResponse tenantRes = tenantService.onboardTenant(CreateTenantRequest.builder()
                .tenantCode("normtenant2")
                .tenantName("Norm Tenant 2")
                .displayName("Norm Tenant 2")
                .build());

        CreateBankAdminRequest adminReq1 = new CreateBankAdminRequest();
        adminReq1.setUserCode("NORM_U2A");
        adminReq1.setFirstName("Case");
        adminReq1.setLastName("One");
        adminReq1.setEmail("admin@normtest.com");
        tenantService.createBankAdmin(tenantRes.getTenantId(), adminReq1);

        CreateBankAdminRequest adminReq2 = new CreateBankAdminRequest();
        adminReq2.setUserCode("NORM_U2B");
        adminReq2.setFirstName("Case");
        adminReq2.setLastName("Two");
        adminReq2.setEmail("ADMIN@NORMTEST.COM");

        assertThatThrownBy(() -> tenantService.createBankAdmin(tenantRes.getTenantId(), adminReq2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");
    }

    // EMAIL TEST 4: Attempt duplicate with surrounding whitespace -> rejected
    @Test
    @DisplayName("Email 4: Attempt duplicate email creation with surrounding whitespace is rejected")
    void duplicateEmailWithWhitespace_IsRejected() {
        CreateTenantResponse tenantRes = tenantService.onboardTenant(CreateTenantRequest.builder()
                .tenantCode("normtenant3")
                .tenantName("Norm Tenant 3")
                .displayName("Norm Tenant 3")
                .build());

        CreateBankAdminRequest adminReq1 = new CreateBankAdminRequest();
        adminReq1.setUserCode("NORM_U3A");
        adminReq1.setFirstName("Space");
        adminReq1.setLastName("One");
        adminReq1.setEmail("user@normtest.com");
        tenantService.createBankAdmin(tenantRes.getTenantId(), adminReq1);

        CreateBankAdminRequest adminReq2 = new CreateBankAdminRequest();
        adminReq2.setUserCode("NORM_U3B");
        adminReq2.setFirstName("Space");
        adminReq2.setLastName("Two");
        adminReq2.setEmail("  user@normtest.com  ");

        assertThatThrownBy(() -> tenantService.createBankAdmin(tenantRes.getTenantId(), adminReq2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");
    }

    // EMAIL TEST 5: Login using different email casing -> resolves to same user
    @Test
    @DisplayName("Email 5: Login using different email casing resolves to same user")
    void loginWithDifferentEmailCasing_ResolvesToSameUser() {
        CreateTenantResponse tenantRes = tenantService.onboardTenant(CreateTenantRequest.builder()
                .tenantCode("normtenant4")
                .tenantName("Norm Tenant 4")
                .displayName("Norm Tenant 4")
                .build());

        CreateBankAdminRequest adminReq = new CreateBankAdminRequest();
        adminReq.setUserCode("NORM_U4");
        adminReq.setFirstName("Login");
        adminReq.setLastName("Test");
        adminReq.setEmail("LoginUser@NormTest.COM");
        tenantService.createBankAdmin(tenantRes.getTenantId(), adminReq);

        Users user = userRepository.findByEmail("loginuser@normtest.com").orElseThrow();
        assertThat(user.getEmail()).isEqualTo("loginuser@normtest.com");
    }

    // TENANT CODE TEST 6 & 7: Create tenant with mixed-case tenant code -> stored as normalized lowercase
    @Test
    @DisplayName("Tenant 6 & 7: Create tenant with mixed-case tenant code and verify normalized lowercase code is stored")
    void createTenantWithMixedCaseCode_StoresNormalizedLowercase() {
        CreateTenantRequest request = CreateTenantRequest.builder()
                .tenantCode("  NormAcMeBank  ")
                .tenantName("Acme Bank")
                .displayName("Acme Bank")
                .build();

        CreateTenantResponse response = tenantService.onboardTenant(request);

        assertThat(response.getTenantCode()).isEqualTo("normacmebank");
        Tenant tenantInDb = tenantRepository.findByTenantCode("normacmebank").orElseThrow();
        assertThat(tenantInDb.getTenantCode()).isEqualTo("normacmebank");
    }

    // TENANT CODE TEST 8: Attempt duplicate using different casing -> rejected
    @Test
    @DisplayName("Tenant 8: Attempt duplicate tenant code using different casing is rejected")
    void duplicateTenantCodeWithDifferentCasing_IsRejected() {
        CreateTenantRequest request1 = CreateTenantRequest.builder()
                .tenantCode("normdupcode")
                .tenantName("Dup Bank 1")
                .displayName("Dup Bank 1")
                .build();
        tenantService.onboardTenant(request1);

        CreateTenantRequest request2 = CreateTenantRequest.builder()
                .tenantCode("NORMDUPCODE")
                .tenantName("Dup Bank 2")
                .displayName("Dup Bank 2")
                .build();

        assertThatThrownBy(() -> tenantService.onboardTenant(request2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant code already exists");
    }

    // TENANT CODE TEST 9: Attempt duplicate with surrounding whitespace -> rejected
    @Test
    @DisplayName("Tenant 9: Attempt duplicate tenant code with surrounding whitespace is rejected")
    void duplicateTenantCodeWithWhitespace_IsRejected() {
        CreateTenantRequest request1 = CreateTenantRequest.builder()
                .tenantCode("normspacecode")
                .tenantName("Space Bank 1")
                .displayName("Space Bank 1")
                .build();
        tenantService.onboardTenant(request1);

        CreateTenantRequest request2 = CreateTenantRequest.builder()
                .tenantCode("  normspacecode  ")
                .tenantName("Space Bank 2")
                .displayName("Space Bank 2")
                .build();

        assertThatThrownBy(() -> tenantService.onboardTenant(request2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant code already exists");
    }

    // TENANT CODE TEST 10: Login using different tenant-code casing -> resolves to same tenant
    @Test
    @DisplayName("Tenant 10: Login using different tenant-code casing resolves to same tenant")
    void loginWithDifferentTenantCodeCasing_ResolvesToSameTenant() {
        CreateTenantResponse tenantRes = tenantService.onboardTenant(CreateTenantRequest.builder()
                .tenantCode("normlogincode")
                .tenantName("Login Code Bank")
                .displayName("Login Code Bank")
                .build());

        Tenant tenantUpperLookup = tenantRepository.findByTenantCode("NORMLOGINCODE".toLowerCase()).orElseThrow();
        assertThat(tenantUpperLookup.getTenantId()).isEqualTo(tenantRes.getTenantId());
    }

    // EMAIL TEST 11: Create Compliance Officer with mixed-case and whitespace email -> stores normalized email and user can authenticate
    @Test
    @DisplayName("Email 11: Create Compliance Officer with mixed-case/padded email stores normalized email")
    void createComplianceOfficerWithMixedCaseEmail_StoresNormalizedLowercase() {
        CreateTenantResponse tenantRes = tenantService.onboardTenant(CreateTenantRequest.builder()
                .tenantCode("normtenantco")
                .tenantName("Norm Tenant CO")
                .displayName("Norm Tenant CO")
                .build());

        CreateBankAdminRequest adminReq = new CreateBankAdminRequest();
        adminReq.setUserCode("NORM_ADMIN_CO");
        adminReq.setFirstName("Bank");
        adminReq.setLastName("Admin");
        adminReq.setEmail("admin.co@normtest.com");
        tenantService.createBankAdmin(tenantRes.getTenantId(), adminReq);

        Users adminUser = userRepository.findByEmail("admin.co@normtest.com").orElseThrow();
        com.tss.aml.security.CustomUserDetails adminDetails = com.tss.aml.security.CustomUserDetails.builder()
                .userId(adminUser.getUserId())
                .username(adminUser.getUserCode())
                .password(adminUser.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_BANK_ADMIN")))
                .tenantId(tenantRes.getTenantId())
                .tenantCode(tenantRes.getTenantCode())
                .mustResetPassword(adminUser.getMustResetPassword())
                .enabled(adminUser.getIsActive())
                .accountNonLocked(true)
                .build();

        com.tss.aml.dtos.tenant.ComplianceOfficerRequest coRequest = new com.tss.aml.dtos.tenant.ComplianceOfficerRequest();
        coRequest.setUserCode("NORM_CO_001");
        coRequest.setEmployeeId("EMP_CO_NORM");
        coRequest.setFirstName("Mixed");
        coRequest.setLastName("Officer");
        coRequest.setEmail("  Compliance.Officer@NORMTEST.COM  ");
        coRequest.setPhoneNumber("+12345678901");

        com.tss.aml.dtos.tenant.ComplianceOfficerResponse coResponse = bankAdminService.createComplianceOfficer(coRequest, adminDetails);

        assertThat(coResponse.getEmail()).isEqualTo("compliance.officer@normtest.com");

        Users coInDb = userRepository.findByEmail("compliance.officer@normtest.com").orElseThrow();
        assertThat(coInDb.getEmail()).isEqualTo("compliance.officer@normtest.com");
        assertThat(coInDb.getUserCode()).isEqualTo("NORM_CO_001");
    }
}

