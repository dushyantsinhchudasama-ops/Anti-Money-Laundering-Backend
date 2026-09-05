package com.tss.aml.controllers;

import com.tss.aml.dtos.batch.BatchUploadResponseDto;
import com.tss.aml.enums.BatchStatus;
import com.tss.aml.security.CustomUserDetails;
import com.tss.aml.services.BatchIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bank/batches")
@RequiredArgsConstructor
public class TransactionBatchController {

    private final BatchIngestionService batchIngestionService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('BANK_ADMIN')")
    public ResponseEntity<BatchUploadResponseDto> uploadTransactionBatch(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        validateAuthenticatedTenantUser(currentUser);

        BatchUploadResponseDto response = batchIngestionService.processBatchUpload(file, currentUser);

        HttpStatus status = response.getStatus() == BatchStatus.REJECTED
                ? HttpStatus.UNPROCESSABLE_ENTITY
                : HttpStatus.ACCEPTED;

        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{batchId}")
    @PreAuthorize("hasAnyRole('BANK_ADMIN', 'COMPLIANCE_OFFICER')")
    public ResponseEntity<BatchUploadResponseDto> getBatchDetails(
            @PathVariable UUID batchId,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        validateAuthenticatedTenantUser(currentUser);

        BatchUploadResponseDto response = batchIngestionService.getBatchDetails(batchId, currentUser);
        return ResponseEntity.ok(response);
    }

    private void validateAuthenticatedTenantUser(CustomUserDetails currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required to access batch operations");
        }
        if (!currentUser.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is disabled");
        }
        if (currentUser.getTenantId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User does not belong to an active bank tenant");
        }
    }
}
