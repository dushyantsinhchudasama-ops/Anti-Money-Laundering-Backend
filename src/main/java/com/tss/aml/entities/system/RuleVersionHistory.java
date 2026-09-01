package com.tss.aml.entities.system;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable snapshot of a Rule change (SRS 3.1.2 — "full version history of all rule changes").
 */
@Getter
@Setter
@Entity
@Table(name = "rule_version_history", schema = "public")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleVersionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "history_id")
    private UUID historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private Rule rule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false)
    private SystemAdmin changedBy;

    @Column(name = "previous_values", columnDefinition = "TEXT")
    private String previousValues;

    @Column(name = "updated_values", columnDefinition = "TEXT")
    private String updatedValues;

    @Builder.Default
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt = LocalDateTime.now();
}
