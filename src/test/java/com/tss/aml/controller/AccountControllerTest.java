package com.tss.aml.controller;

import com.tss.aml.controllers.AccountController;
import com.tss.aml.dtos.account.AccountResponse;
import com.tss.aml.enums.AccountType;
import com.tss.aml.enums.RiskRating;
import com.tss.aml.services.interfaces.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AccountControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/accounts with BANK_ADMIN role succeeds and returns paginated accounts")
    @WithMockUser(roles = "BANK_ADMIN")
    void getAccounts_BankAdmin_ReturnsPaginatedAccounts() throws Exception {
        AccountResponse response = AccountResponse.builder()
                .accountId(UUID.randomUUID())
                .accountNumber("ACC123456")
                .accountHolderName("John Doe")
                .accountType(AccountType.SAVINGS)
                .bankName("Test Bank")
                .countryCode("US")
                .riskRating(RiskRating.LOW)
                .openedAt(LocalDateTime.now())
                .build();

        when(accountService.getAllAccounts(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/accounts?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].accountNumber").value("ACC123456"))
                .andExpect(jsonPath("$.content[0].accountHolderName").value("John Doe"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/accounts with COMPLIANCE_OFFICER role succeeds and returns paginated accounts")
    @WithMockUser(roles = "COMPLIANCE_OFFICER")
    void getAccounts_ComplianceOfficer_ReturnsPaginatedAccounts() throws Exception {
        AccountResponse response = AccountResponse.builder()
                .accountId(UUID.randomUUID())
                .accountNumber("ACC789012")
                .accountHolderName("Jane Smith")
                .accountType(AccountType.CURRENT)
                .bankName("Test Bank")
                .countryCode("US")
                .riskRating(RiskRating.HIGH)
                .openedAt(LocalDateTime.now())
                .build();

        when(accountService.getAllAccounts(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].accountNumber").value("ACC789012"));
    }

    @Test
    @DisplayName("GET /api/v1/accounts with unauthorized role returns 403 Forbidden")
    @WithMockUser(roles = "USER")
    void getAccounts_UnauthorizedRole_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/accounts without authentication returns 401 Unauthorized or 403 Forbidden")
    void getAccounts_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isForbidden());
    }
}
