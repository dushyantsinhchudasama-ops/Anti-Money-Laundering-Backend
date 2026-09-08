package com.tss.aml.dtos.tenant;

import com.tss.aml.enums.NoteType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseNoteCreateRequest {

    @NotNull(message = "Note type is required")
    private NoteType noteType;

    @NotBlank(message = "Content must not be blank")
    private String content;
}
