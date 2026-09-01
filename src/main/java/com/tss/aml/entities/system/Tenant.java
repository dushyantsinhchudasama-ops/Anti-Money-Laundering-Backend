package com.tss.aml.entities.system;

import com.tss.aml.entities.common.BaseEntity;
import com.tss.aml.enums.TenantStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.UUID;

/**
 * A registered financial institution (Bank) — an isolated tenant (SRS 3.1.3).
 * Tenant-scoped data (users, transactions, alerts, cases...) lives in this bank's
 * own database schema ({@link #schemaName}), resolved at runtime via
 * TenantIdentifierResolver — it is NOT modelled as JPA relations from this entity.
 */
@NoArgsConstructor
@Getter @Setter
@Entity
@Table(name = "tenants", schema = "public")
@SuperBuilder
public class Tenant extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "tenant_id")
    @ToString.Include
    private UUID tenantId;

    @Column(name = "tenant_code", nullable = false, unique = true)
    @ToString.Include
    private String tenantCode;

    @Column(name = "tenant_name", nullable = false)
    @ToString.Include
    private String tenantName;

    @Column(name = "display_name", nullable = false)
    @ToString.Include
    private String displayName;

    @Column(name = "schema_name", unique = true, nullable = false)
    @ToString.Include
    private String schemaName;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TenantStatus status = TenantStatus.ONBOARDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "onboarded_by_admin_id", nullable = false)
    private SystemAdmin onboardedByAdmin;

    // Active/Draft/Paused rules assigned to this bank (public-schema mapping only;
    // do NOT relate this to tenant-schema entities like TransactionBatch/Alert/Case).
    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BankRuleAssignment> ruleAssignments;
}
