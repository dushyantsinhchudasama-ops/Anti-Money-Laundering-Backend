package com.tss.aml.entities.tenant;

import com.tss.aml.entities.common.BaseEntity;
import com.tss.aml.entities.system.Users;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The Suspicious Activity / Transaction Report (SRS 3.3.6) — the primary regulatory
 * output of the investigation process. Permanently linked to its originating case;
 * cannot be deleted once filed (SRS 4.4).
 */
@Getter
@Setter
@Entity
@Table(name = "sar_str")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SarStr extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "sar_str_id")
    private UUID sarStrId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false, unique = true)
    private AmlCase amlCase;

    @Column(name = "typology_category", nullable = false, length = 100)
    private String typologyCategory;

    @Column(name = "description_of_activity", nullable = false, columnDefinition = "TEXT")
    private String descriptionOfActivity;

    @Column(name = "basis_for_suspicion", nullable = false, columnDefinition = "TEXT")
    private String basisForSuspicion;

    @Column(name = "supporting_evidence", columnDefinition = "TEXT")
    private String supportingEvidence;

    @Column(name = "reference_number", unique = true)
    private String referenceNumber;

    @Column(name = "pdf_reference")
    private String pdfReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filed_by")
    private Users filedBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
}
