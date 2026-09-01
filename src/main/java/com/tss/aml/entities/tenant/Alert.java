package com.tss.aml.entities.tenant;

import com.tss.aml.entities.common.BaseEntity;
import com.tss.aml.entities.system.Rule;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.AlertStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * A system-generated alert raised when a transaction matches a rule (SRS 3.1.2 / 4.4).
 * Alerts are never manually created or deleted by any actor.
 */
@Getter
@Setter
@Entity
@Table(name = "alerts")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Alert extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "alert_id")
    private UUID alertId;

    @Column(name = "alert_code", nullable = false, unique = true)
    private String alertCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private FinancialTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private Rule rule;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 50)
    private AlertSeverity severity;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "alert_status", nullable = false, length = 50)
    private AlertStatus alertStatus = AlertStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private AmlCase amlCase;
}
