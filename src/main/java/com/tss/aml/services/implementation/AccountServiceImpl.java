package com.tss.aml.services.implementation;

import com.tss.aml.dtos.account.AccountResponse;
import com.tss.aml.entities.tenant.Account;
import com.tss.aml.repositories.AccountRepository;
import com.tss.aml.services.interfaces.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<AccountResponse> getAllAccounts(Pageable pageable) {
        log.debug("Fetching paginated accounts for currently active tenant context");
        Page<Account> accounts = accountRepository.findAll(pageable);
        return accounts.map(acc -> AccountResponse.builder()
                .accountId(acc.getAccountId())
                .accountNumber(acc.getAccountNumber())
                .accountHolderName(acc.getAccountHolderName())
                .accountType(acc.getAccountType())
                .bankName(acc.getBankName())
                .countryCode(acc.getCountryCode())
                .riskRating(acc.getRiskRating())
                .openedAt(acc.getOpenedAt())
                .build());
    }
}
