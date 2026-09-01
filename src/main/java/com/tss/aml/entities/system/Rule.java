package com.tss.aml.entities.system;

import com.tss.aml.entities.common.BaseEntity;
import com.tss.aml.enums.AlertSeverity;
import com.tss.aml.enums.RuleStatus;
import com.tss.aml.enums.RuleTypology;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

/**
 * A codified AML typology rule (SRS 3.1.2). Rules are created and versioned by the
 * AML System Admin, then assigned to individual Banks via {@link BankRuleAssignment}.
 */
@Getter @Setter
@Entity
@Table(name = "rules", schema = "public")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Rule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "rule_id")
    @ToString.Include
    private UUID ruleId;

    @Column(name = "rule_code", unique = true, nullable = false)
    @ToString.Include
    private String ruleCode;

    @Column(name = "rule_name", nullable = false)
    @ToString.Include
    private String ruleName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "typology", nullable = false, columnDefinition = "rule_typology_enum")
    private RuleTypology typology;

    /** Rule-specific thresholds/config, e.g. {"windowDays":7,"minCount":5,"amountThreshold":900000} */
    @Column(name = "parameters", columnDefinition = "TEXT")
    private String parameters;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "default_severity", nullable = false, columnDefinition = "alert_severity_enum")
    private AlertSeverity defaultSeverity;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "rule_status_enum")
    private RuleStatus status = RuleStatus.DRAFT;

    @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RuleVersionHistory> versionHistory;

    @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BankRuleAssignment> bankAssignments;
}
