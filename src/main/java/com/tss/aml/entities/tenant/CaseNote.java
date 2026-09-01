package com.tss.aml.entities.tenant;

import com.tss.aml.entities.system.Users;
import com.tss.aml.enums.NoteType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single investigation note (SRS 3.3.4). Append-only — once submitted, a note
 * cannot be edited or deleted by any user, including admins.
 */
@Getter
@Setter
@Entity
@Table(name = "case_note")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseNote {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "note_id")
    private UUID noteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private AmlCase amlCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private Users author;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 50)
    private NoteType noteType;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
