package com.tss.aml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.aml.dtos.auth.LoginRequest;
import com.tss.aml.dtos.auth.ResetPasswordRequest;
import com.tss.aml.dtos.tenant.ComplianceOfficerRequest;
import com.tss.aml.dtos.tenant.CreateBankAdminRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ValidationErrorHandlingIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("Test 1: Required email rejects blank input")
    void requiredEmailRejectsBlankInput() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("");
        loginRequest.setPassword("Password123!");

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.message").value("Email is required."))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertThat(content).doesNotContain("Validation failed for argument");
        assertThat(content).doesNotContain("MethodArgumentNotValidException");
    }

    @Test
    @DisplayName("Test 2: Invalid email format is rejected")
    void invalidEmailFormatIsRejected() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("invalid-email-format");
        loginRequest.setPassword("Password123!");

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.message").value("Please enter a valid email address."))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertThat(content).doesNotContain("Validation failed for argument");
        assertThat(content).doesNotContain("MethodArgumentNotValidException");
        assertThat(content).doesNotContain("invalid-email-format");
    }

    @Test
    @DisplayName("Test 3: Valid email passes Bean Validation")
    void validEmailPassesValidation() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("valid.user@aml.com");
        loginRequest.setPassword("Password123!");

        // Fails authentication (401 Unauthorized), NOT 400 Bad Request (Validation failed)
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin@aml.com", roles = "SYSTEM_ADMIN")
    @DisplayName("Test 4: Invalid phone number format is rejected on CreateBankAdminRequest")
    void invalidPhoneFormatIsRejectedOnCreateBankAdmin() throws Exception {
        CreateBankAdminRequest request = new CreateBankAdminRequest();
        request.setUserCode("ADMIN_001");
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setEmail("john.doe@bank.com");
        request.setPhoneNumber("invalid-phone-xyz");

        MvcResult result = mockMvc.perform(post("/api/v1/admin/tenants/" + UUID.randomUUID() + "/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.message").value("Please enter a valid phone number."))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertThat(content).doesNotContain("Validation failed for argument");
        assertThat(content).doesNotContain("invalid-phone-xyz");
    }

    @Test
    @WithMockUser(username = "admin@aml.com", roles = "SYSTEM_ADMIN")
    @DisplayName("Test 5: Valid phone number is accepted on CreateBankAdminRequest")
    void validPhoneFormatIsAcceptedOnCreateBankAdmin() throws Exception {
        CreateBankAdminRequest request = new CreateBankAdminRequest();
        request.setUserCode("ADMIN_001");
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setEmail("john.doe@bank.com");
        request.setPhoneNumber("+12345678901");

        // Fails with IllegalArgumentException (400 "Tenant not found") in service layer, NOT 400 "Validation failed"
        MvcResult result = mockMvc.perform(post("/api/v1/admin/tenants/" + UUID.randomUUID() + "/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertThat(content).doesNotContain("Please enter a valid phone number.");
    }

    @Test
    @DisplayName("Test 6: LoginRequest contains NO phoneNumber or mobileNumber field")
    void loginRequestContainsNoPhoneField() {
        Field[] fields = LoginRequest.class.getDeclaredFields();
        boolean hasPhoneField = Arrays.stream(fields)
                .anyMatch(field -> field.getName().equalsIgnoreCase("phoneNumber") || field.getName().equalsIgnoreCase("mobileNumber"));
        assertThat(hasPhoneField).isFalse();
    }

    @Test
    @DisplayName("Test 7: Clean validation error response format is returned without stack trace leakage")
    void cleanValidationErrorResponseFormat() throws Exception {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("bad-email")
                .currentPassword("OldPass123!")
                .newPassword("NewPass123!")
                .confirmPassword("NewPass123!")
                .build();

        MvcResult result = mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.message").value("Please enter a valid email address."))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertThat(content).doesNotContain("stackTrace");
        assertThat(content).doesNotContain("MethodArgumentNotValidException");
        assertThat(content).doesNotContain("rejectedValue");
        assertThat(content).doesNotContain("bad-email");
    }
}
