package com.tss.aml.dtos.tenant;

import com.tss.aml.enums.NoteType;
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
public class CaseNoteResponse {

    private UUID noteId;
    private NoteType noteType;
    private String content;
    private UUID authorId;
    private String authorName;
    private String authorEmail;
    private LocalDateTime createdAt;
}
