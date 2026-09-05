package com.tss.aml.dtos.batch;

import com.tss.aml.enums.TransactionDirection;
import com.tss.aml.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedTransactionRowDto {
    private String txnNo;
    private String originatorAccountNo;
    private String originatorName;
    private BigDecimal amount;
    private String currency;
    private TransactionType txnType;
    private TransactionDirection direction;
    private String counterpartyName;
    private String counterpartyAccountNo;
    private String counterpartyBank;
    private String counterpartyCountryCode;
    private LocalDateTime txnTimestamp;
    private String countryCode;
}
