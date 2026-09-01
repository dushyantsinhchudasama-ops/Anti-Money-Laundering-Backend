package com.tss.aml.entities.tenant;

import com.tss.aml.entities.common.BaseEntity;
import com.tss.aml.enums.CaseStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A formal investigation record created when a Compliance Officer begins reviewing
 * one or more alerts (SRS 3.2.5 / 3.3). Investigation content lives in CaseNote,
 * not on this entity, so it stays append-only and auditable.
 */
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "aml_case")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AmlCase extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "case_id")
    @ToString.Include
    private UUID caseId;

    @Column(name = "case_code", nullable = false, length = 20, unique = true)
    @ToString.Include
    private String caseCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private TenantUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private TenantUser assignedTo;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "case_status_enum")
    private CaseStatus status = CaseStatus.OPEN;

    // Mandatory rationale on closure (SRS 3.3.7)
    @Column(name = "false_positive_rationale", columnDefinition = "TEXT")
    private String falsePositiveRationale;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @OneToMany(mappedBy = "amlCase", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<Alert> alerts;

    @OneToMany(mappedBy = "amlCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseNote> notes;

    @OneToMany(mappedBy = "amlCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Escalation> escalations;

    @OneToOne(mappedBy = "amlCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private SarStr sarStr;
}
