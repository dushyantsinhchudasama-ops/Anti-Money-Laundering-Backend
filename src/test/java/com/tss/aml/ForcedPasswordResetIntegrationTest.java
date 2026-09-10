package com.tss.aml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.aml.config.SystemAdminDataInitializer;
import com.tss.aml.dtos.auth.LoginRequest;
import com.tss.aml.dtos.auth.ResetPasswordRequest;
import com.tss.aml.dtos.tenant.CreateBankAdminRequest;
import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.entities.system.Tenant;
import com.tss.aml.entities.system.Users;
import com.tss.aml.enums.TenantStatus;
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
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ForcedPasswordResetIntegrationTest {

        @MockitoBean
        private JavaMailSender mailSender;

        @MockitoSpyBean
        private com.tss.aml.services.EmailService emailService;

        @Autowired
        private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

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
        private com.tss.aml.services.TenantMigrationService tenantMigrationService;

        @Autowired
        private com.tss.aml.repositories.AuditLogRepository auditLogRepository;

        @Autowired
        private com.tss.aml.repositories.NotificationRepository notificationRepository;

        @Autowired
        private com.tss.aml.repositories.AlertRepository alertRepository;

        @Autowired
        private com.tss.aml.repositories.AmlCaseRepository amlCaseRepository;

        @Autowired
        private com.tss.aml.repositories.FinancialTransactionRepository financialTransactionRepository;

        @Autowired
        private com.tss.aml.repositories.TransactionBatchRepository batchRepository;

        @Autowired
        private com.tss.aml.repositories.BatchValidationErrorRepository batchValidationErrorRepository;

        private SystemAdmin systemAdmin;
        private String systemAdminToken;
        private Tenant hdfcTenant;

        @BeforeEach
        void setUp() {
                mockMvc = MockMvcBuilders.webAppContextSetup(context)
                                .apply(SecurityMockMvcConfigurers.springSecurity())
                                .build();

                TenantContext.clear();
                SecurityContextHolder.clearContext();

                try {
                        List<String> schemas = jdbcTemplate.queryForList(
                                        "SELECT DISTINCT table_schema FROM information_schema.tables WHERE table_name IN ('transaction_batch', 'alerts', 'audit_log', 'aml_case')",
                                        String.class);
                        for (String s : schemas) {
                                if (!"information_schema".equalsIgnoreCase(s) && !"pg_catalog".equalsIgnoreCase(s)) {
                                        List<String> existingTables = jdbcTemplate.queryForList(
                                                        "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_name IN ('case_note', 'escalation', 'sar_str', 'audit_log', 'notification', 'alerts', 'aml_case', 'batch_validation_error', 'financial_transaction', 'transaction_batch', 'account')",
                                                        String.class, s);
                                        for (String t : existingTables) {
                                                try {
                                                        jdbcTemplate.execute("TRUNCATE TABLE \"" + s + "\".\"" + t
                                                                        + "\" CASCADE");
                                                } catch (Exception ignored) {
                                                }
                                        }
                                }
                        }
                } catch (Exception ignored) {
                }

                userRepository.findByEmail("reset_admin@hdfc.com").ifPresent(userRepository::delete);
                userRepository.findByUserCode("RESET_HDFC_ADMIN").ifPresent(userRepository::delete);

                if (systemAdminRepository.count() == 0) {
                        initializer.run(null);
                }

                systemAdmin = systemAdminRepository.findAll().get(0);
                UserDetails adminDetails = customUserDetailsService.loadUserByUsername(systemAdmin.getEmail());
                Authentication auth = new UsernamePasswordAuthenticationToken(
                                adminDetails, null, adminDetails.getAuthorities());
                systemAdminToken = jwtTokenProvider.generateToken(auth);

                hdfcTenant = tenantRepository.findByTenantCode("HDFC")
                                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                                                .tenantCode("HDFC")
                                                .tenantName("HDFC Bank")
                                                .displayName("HDFC Bank")
                                                .schemaName("tenant_hdfc")
                                                .status(TenantStatus.ACTIVE)
                                                .onboardedByAdmin(systemAdmin)
                                                .build()));

                tenantMigrationService.migrateTenantSchema(hdfcTenant.getSchemaName());
        }

        @Test
        @DisplayName("SRS 3.2.1 End-to-End Forced Password Reset & Tenant Isolation Flow")
        void fullForcedPasswordResetFlow() throws Exception {
                // Step 1: Create Bank Admin via SystemAdmin
                CreateBankAdminRequest createRequest = new CreateBankAdminRequest();
                createRequest.setUserCode("RESET_HDFC_ADMIN");
                createRequest.setFirstName("Reset");
                createRequest.setLastName("Admin");
                createRequest.setEmail("reset_admin@hdfc.com");

                MvcResult createResult = mockMvc
                                .perform(post("/api/v1/admin/tenants/" + hdfcTenant.getTenantId() + "/users")
                                                .header("Authorization", "Bearer " + systemAdminToken)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn();

                JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
                assertThat(createResponse.has("temporaryPassword")).isFalse();

                ArgumentCaptor<String> tempPassCaptor = ArgumentCaptor.forClass(String.class);
                verify(emailService).sendBankAdminWelcomeEmail(
                        eq("reset_admin@hdfc.com"),
                        any(),
                        any(),
                        any(),
                        tempPassCaptor.capture()
                );
                String tempPassword = tempPassCaptor.getValue();
                assertThat(tempPassword).isNotNull().startsWith("TmpAdmin@");

                // Step 2: First Login with Temporary Password -> mustResetPassword: true
                LoginRequest loginRequest = new LoginRequest();
                loginRequest.setEmail("reset_admin@hdfc.com");
                loginRequest.setPassword(tempPassword);

                MvcResult firstLoginResult = mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn();

                JsonNode firstLoginResponse = objectMapper
                                .readTree(firstLoginResult.getResponse().getContentAsString());
                String tempToken = firstLoginResponse.get("accessToken").asText();
                assertThat(firstLoginResponse.get("mustResetPassword").asBoolean()).isTrue();

                // Step 3: Operational Endpoint Attempt before Reset -> Blocked with 428
                // Precondition Required
                mockMvc.perform(get("/api/v1/bank/batches/" + UUID.randomUUID())
                                .header("Authorization", "Bearer " + tempToken))
                                .andExpect(status().is(428));

                // Step 4: Password Reset with Weak Password -> 400 Bad Request
                ResetPasswordRequest weakPasswordRequest = ResetPasswordRequest.builder()
                                .email("reset_admin@hdfc.com")
                                .tenantCode("HDFC")
                                .currentPassword(tempPassword)
                                .newPassword("weak")
                                .confirmPassword("weak")
                                .build();

                mockMvc.perform(post("/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(weakPasswordRequest)))
                                .andExpect(status().isBadRequest());

                // Step 5: Password Reset with Compliant Password -> Success
                String newPassword = "NewSecurePassword@123";
                ResetPasswordRequest validResetRequest = ResetPasswordRequest.builder()
                                .email("reset_admin@hdfc.com")
                                .tenantCode("HDFC")
                                .currentPassword(tempPassword)
                                .newPassword(newPassword)
                                .confirmPassword(newPassword)
                                .build();

                MvcResult resetResult = mockMvc.perform(post("/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validResetRequest)))
                                .andExpect(status().isOk())
                                .andReturn();

                JsonNode resetResponse = objectMapper.readTree(resetResult.getResponse().getContentAsString());
                String newToken = resetResponse.get("accessToken").asText();
                assertThat(resetResponse.get("mustResetPassword").asBoolean()).isFalse();

                // Verify Database state
                Users userInDb = userRepository.findByEmail("reset_admin@hdfc.com").orElseThrow();
                assertThat(userInDb.getMustResetPassword()).isFalse();

                // Step 6: Operational Endpoint Attempt after Reset -> Not blocked by 428
                // (returns 404 for non-existent batch)
                mockMvc.perform(get("/api/v1/bank/batches/" + UUID.randomUUID())
                                .header("Authorization", "Bearer " + newToken))
                                .andExpect(status().isNotFound());

                // Step 7: Subsequent Login with New Password -> mustResetPassword: false
                LoginRequest subsequentLoginRequest = new LoginRequest();
                subsequentLoginRequest.setEmail("reset_admin@hdfc.com");
                subsequentLoginRequest.setPassword(newPassword);

                MvcResult subsequentLoginResult = mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(subsequentLoginRequest)))
                                .andExpect(status().isOk())
                                .andReturn();

                JsonNode subsequentLoginResponse = objectMapper
                                .readTree(subsequentLoginResult.getResponse().getContentAsString());
                assertThat(subsequentLoginResponse.get("mustResetPassword").asBoolean()).isFalse();
        }
}
