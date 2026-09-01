package com.tss.aml.entities.tenant;

import com.tss.aml.entities.system.Users;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A case escalation event (SRS 3.3.5). Escalating does NOT close the case —
 * the case remains active and under investigation.
 */
@Getter
@Setter
@Entity
@Table(name = "escalation")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Escalation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "escalation_id")
    private UUID escalationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private AmlCase amlCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escalated_by", nullable = false)
    private Users escalatedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Users recipient;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Builder.Default
    @Column(name = "escalated_at", nullable = false, updatable = false)
    private LocalDateTime escalatedAt = LocalDateTime.now();
}
