package com.tss.aml.dtos.account;

import com.tss.aml.enums.AccountType;
import com.tss.aml.enums.RiskRating;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {
    private UUID accountId;
    private String accountNumber;
    private String accountHolderName;
    private AccountType accountType;
    private String bankName;
    private String countryCode;
    private RiskRating riskRating;
    private LocalDateTime openedAt;
}
