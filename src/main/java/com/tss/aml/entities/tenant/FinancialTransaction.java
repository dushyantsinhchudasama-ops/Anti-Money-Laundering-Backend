package com.tss.aml.entities.tenant;

import com.tss.aml.entities.common.BaseEntity;
import com.tss.aml.enums.TransactionDirection;
import com.tss.aml.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single ingested transaction. Read-only for all actors once ingested (SRS 4.4).
 */
@Data
@Entity
@Table(name = "financial_transaction")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class FinancialTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "transaction_id")
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private TransactionBatch batch;

    @Column(name = "txn_no", nullable = false)
    private String txnNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "originator_account_id", nullable = false)
    private Account originatorAccount;

    @Column(name = "amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 50)
    private TransactionType txnType;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 50)
    private TransactionDirection direction;

    // Beneficiary / counterparty — external party, captured as plain fields (SRS 3.3.3)
    @Column(name = "counterparty_name", length = 200)
    private String counterpartyName;

    @Column(name = "counterparty_account_no", length = 30)
    private String counterpartyAccountNo;

    @Column(name = "counterparty_bank", length = 200)
    private String counterpartyBank;

    @Column(name = "counterparty_country_code", length = 2)
    private String counterpartyCountryCode;

    @Column(name = "txn_timestamp", nullable = false)
    private LocalDateTime txnTimestamp;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;
}
