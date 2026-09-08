package com.tss.aml.controller;

import com.tss.aml.controllers.TransactionBatchController;
import com.tss.aml.dtos.batch.BatchUploadResponseDto;
import com.tss.aml.enums.BatchStatus;
import com.tss.aml.security.CustomUserDetails;
import com.tss.aml.services.interfaces.BatchIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class TransactionBatchSecurityTest {

    private TransactionBatchController controller;
    private BatchIngestionService batchIngestionService;

    private UUID activeTenantId;
    private CustomUserDetails validUserDetails;

    @BeforeEach
    void setUp() {
        batchIngestionService = Mockito.mock(BatchIngestionService.class);
        controller = new TransactionBatchController(batchIngestionService);

        activeTenantId = UUID.randomUUID();

        validUserDetails = CustomUserDetails.builder()
                .userId(UUID.randomUUID())
                .username("admin@hdfc.com")
                .tenantId(activeTenantId)
                .tenantCode("HDFC_BANK")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_BANK_ADMIN")))
                .enabled(true)
                .accountNonLocked(true)
                .build();
    }

    @Test
    void testUnauthenticatedUser_isRejectedWithUnauthorized() {
        MockMultipartFile file = new MockMultipartFile("file", "batch.xlsx", "text/plain", "data".getBytes());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.uploadTransactionBatch(file, null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void testInactiveUser_isRejectedWithForbidden() {
        CustomUserDetails disabledUserDetails = CustomUserDetails.builder()
                .userId(UUID.randomUUID())
                .username("disabled@hdfc.com")
                .tenantId(activeTenantId)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_BANK_ADMIN")))
                .enabled(false)
                .build();

        MockMultipartFile file = new MockMultipartFile("file", "batch.xlsx", "text/plain", "data".getBytes());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.uploadTransactionBatch(file, disabledUserDetails)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void testUserWithoutActiveTenant_isRejectedWithForbidden() {
        CustomUserDetails noTenantUserDetails = CustomUserDetails.builder()
                .userId(UUID.randomUUID())
                .username("notenant@bank.com")
                .tenantId(null)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_BANK_ADMIN")))
                .enabled(true)
                .build();

        MockMultipartFile file = new MockMultipartFile("file", "batch.xlsx", "text/plain", "data".getBytes());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.uploadTransactionBatch(file, noTenantUserDetails)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void testValidTenantUser_successfullyUploadsBatch() {
        MockMultipartFile file = new MockMultipartFile("file", "batch.xlsx", "text/plain", "data".getBytes());

        BatchUploadResponseDto mockResponse = BatchUploadResponseDto.builder()
                .batchId(UUID.randomUUID())
                .batchCode("BATCH-001")
                .status(BatchStatus.PROCESSED_NO_ALERTS)
                .totalRecords(10)
                .alertsGeneratedCount(0)
                .build();

        when(batchIngestionService.processBatchUpload(eq(file), any(CustomUserDetails.class))).thenReturn(mockResponse);

        ResponseEntity<BatchUploadResponseDto> response = controller.uploadTransactionBatch(file, validUserDetails);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BATCH-001", response.getBody().getBatchCode());
    }
}
