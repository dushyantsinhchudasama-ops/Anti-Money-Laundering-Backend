package com.tss.aml.dtos.tenant;

import com.tss.aml.enums.FiuTypologyCategory;
import com.tss.aml.enums.SarStrType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SarStrFilingRequest {

    @NotNull(message = "Report type (SAR/STR) is required")
    private SarStrType reportType;

    @NotNull(message = "Typology category is required")
    private FiuTypologyCategory typologyCategory;

    @NotBlank(message = "Description of suspicious activity is required")
    private String descriptionOfActivity;

    @NotBlank(message = "Basis for suspicion is required")
    private String basisForSuspicion;

    @NotBlank(message = "Supporting evidence/account relationships information is required")
    private String supportingEvidence;
}
