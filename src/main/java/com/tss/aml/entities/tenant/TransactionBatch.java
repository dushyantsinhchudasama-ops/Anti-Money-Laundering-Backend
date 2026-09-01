package com.tss.aml.entities.tenant;

import com.tss.aml.enums.BatchStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A single Excel transaction batch upload (SRS 3.2.3). A batch is fully accepted
 * or fully rejected — never partially processed.
 */
@Getter
@Setter
@Entity
@Table(name = "transaction_batch")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "batch_code", nullable = false, unique = true)
    private String batchCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private TenantUser uploadedBy;

    @Column(name = "file_reference", nullable = false)
    private String fileReference;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "batch_status_enum")
    private BatchStatus status = BatchStatus.QUEUED;

    @Column(name = "total_records")
    private Integer totalRecords;

    @Column(name = "alerts_generated_count")
    private Integer alertsGeneratedCount;

    @Builder.Default
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FinancialTransaction> transactions;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BatchValidationError> validationErrors;
}
