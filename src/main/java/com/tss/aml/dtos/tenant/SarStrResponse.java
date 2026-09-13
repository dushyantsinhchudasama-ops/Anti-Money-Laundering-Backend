package com.tss.aml.dtos.tenant;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.aml.enums.SarStrType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SarStrResponse {

    private UUID sarStrId;
    private UUID caseId;
    private String caseCode;
    private SarStrType reportType;
    private String typologyCategory;
    private String descriptionOfActivity;
    private String basisForSuspicion;
    private String supportingEvidence;
    private String referenceNumber;
    private String pdfReference;
    private UUID filedById;
    private String filedByName;
    private String filedByEmail;
    @JsonAlias({"filedAt"})
    private LocalDateTime submittedAt;

    // Subject & Account Information
    private String accountNumber;
    private String accountHolderName;
}
