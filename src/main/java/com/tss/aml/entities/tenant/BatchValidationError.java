package com.tss.aml.entities.tenant;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;


@Getter
@Setter
@Entity
@Table(name = "batch_validation_error")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchValidationError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "error_id")
    private Long errorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private TransactionBatch batch;

    @Column(name = "row_number", nullable = false)
    private Integer rowNumber;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "error_message", nullable = false, columnDefinition = "TEXT")
    private String errorMessage;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
