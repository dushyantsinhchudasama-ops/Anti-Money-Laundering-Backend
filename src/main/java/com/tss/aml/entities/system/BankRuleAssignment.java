package com.tss.aml.entities.system;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Direct assignment of an Active Rule to a Bank (SRS 3.1.3).
 * Replaces the old Scenario/TenantScenarioMapping indirection — the SRS only
 * describes a flat Bank -> Rule relationship, nothing richer.
 */
@Getter
@Setter
@Entity
@Table(name = "bank_rule_assignment", schema = "public")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankRuleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "assignment_id")
    private UUID assignmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private Rule rule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by", nullable = false)
    private SystemAdmin assignedBy;

    @Builder.Default
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();
}
