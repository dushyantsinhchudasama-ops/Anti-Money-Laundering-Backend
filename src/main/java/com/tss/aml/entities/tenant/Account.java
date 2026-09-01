package com.tss.aml.entities.tenant;

import com.tss.aml.entities.common.BaseEntity;
import com.tss.aml.enums.AccountType;
import com.tss.aml.enums.RiskRating;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * An account belonging to this bank's own customer (the transaction originator).
 * Counterparty (beneficiary) details are captured directly on FinancialTransaction
 * as plain fields, since the counterparty is external and not onboarded as a
 * customer of this institution (SRS Out-of-Scope 5 — no core-banking integration,
 * no separate customer master ingestion; all data enters via transaction batch only).
 */
@Data
@Entity
@Table(name = "account")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Account extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "account_number", nullable = false, unique = true, length = 30)
    private String accountNumber;

    @Column(name = "account_holder_name", nullable = false, length = 200)
    private String accountHolderName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "account_type", nullable = false, columnDefinition = "account_type_enum")
    private AccountType accountType;

    @Column(name = "bank_name", length = 200)
    private String bankName;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    // Customer Risk Rating (SRS 3.3.3)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "risk_rating", columnDefinition = "risk_rating_enum")
    private RiskRating riskRating;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @OneToMany(mappedBy = "originatorAccount", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FinancialTransaction> transactions;
}
