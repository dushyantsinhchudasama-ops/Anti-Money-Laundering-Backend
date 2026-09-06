package com.tss.aml.dtos.tenant;

import com.tss.aml.enums.CaseStatus;
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
public class CaseResponse {
    private UUID caseId;
    private String caseCode;
    private CaseStatus status;
    private UUID createdById;
    private String createdByName;
    private UUID assignedToId;
    private String assignedToName;
    private int alertCount;
    private List<AlertResponse> alerts;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
}
