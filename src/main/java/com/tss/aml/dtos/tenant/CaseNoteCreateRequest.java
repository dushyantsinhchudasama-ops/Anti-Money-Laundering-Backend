package com.tss.aml.dtos.tenant;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.tss.aml.enums.NoteType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseNoteCreateRequest {

    private NoteType noteType;

    @NotBlank(message = "Content must not be blank")
    @JsonAlias({"noteText", "content"})
    private String content;

    public NoteType getNoteType() {
        return noteType != null ? noteType : NoteType.OBSERVATION;
    }
}
