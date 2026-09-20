package com.tss.aml.services.interfaces;

import com.tss.aml.dtos.batch.BatchUploadResponseDto;
import com.tss.aml.entities.system.Users;
import com.tss.aml.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface BatchIngestionService {
    @Transactional
    BatchUploadResponseDto processBatchUpload(MultipartFile file, CustomUserDetails currentUser);

    @Transactional(readOnly = true)
    BatchUploadResponseDto getBatchDetails(UUID batchId, CustomUserDetails currentUser);

    @Transactional(readOnly = true)
    Page<BatchUploadResponseDto> getAllBatchesForTenant(Pageable pageable, CustomUserDetails currentUser);
}

