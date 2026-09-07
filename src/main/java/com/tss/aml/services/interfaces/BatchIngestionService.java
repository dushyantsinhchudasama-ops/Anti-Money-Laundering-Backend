package com.tss.aml.services.interfaces;

import com.tss.aml.dtos.batch.BatchUploadResponseDto;
import com.tss.aml.entities.system.Users;
import com.tss.aml.security.CustomUserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface BatchIngestionService {
    @Transactional
    BatchUploadResponseDto processBatchUpload(MultipartFile file, CustomUserDetails currentUser);

    @Transactional(readOnly = true)
    BatchUploadResponseDto getBatchDetails(UUID batchId, CustomUserDetails currentUser);
}
