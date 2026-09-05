package com.tss.aml.dtos.batch;

import com.tss.aml.enums.BatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchUploadResponseDto {
    private UUID batchId;
    private String batchCode;
    private BatchStatus status;
    private Integer totalRecords;
    private Integer alertsGeneratedCount;
    private LocalDateTime uploadedAt;
    private List<BatchValidationErrorDto> errors;
}
