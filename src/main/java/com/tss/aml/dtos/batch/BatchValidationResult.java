package com.tss.aml.dtos.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchValidationResult<T> {
    private boolean valid;
    private List<BatchValidationErrorDto> errors;
    private List<T> parsedData;
}
