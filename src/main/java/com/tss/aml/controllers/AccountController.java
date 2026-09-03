package com.tss.aml.controllers;

import com.tss.aml.dtos.account.AccountResponse;
import com.tss.aml.entities.tenant.Account;
import com.tss.aml.repositories.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private final AccountRepository accountRepository;

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        log.debug("Fetching accounts for currently active tenant context");
        List<Account> accounts = accountRepository.findAll();
        List<AccountResponse> response = accounts.stream()
                .map(acc -> AccountResponse.builder()
                        .accountId(acc.getAccountId())
                        .accountNumber(acc.getAccountNumber())
                        .accountHolderName(acc.getAccountHolderName())
                        .accountType(acc.getAccountType())
                        .bankName(acc.getBankName())
                        .countryCode(acc.getCountryCode())
                        .riskRating(acc.getRiskRating())
                        .openedAt(acc.getOpenedAt())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}
