package com.tss.aml.dtos.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchValidationErrorDto {
    private Integer rowNumber;
    private String fieldName;
    private String errorMessage;
}
