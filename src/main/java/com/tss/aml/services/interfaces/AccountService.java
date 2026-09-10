package com.tss.aml.services.interfaces;

import com.tss.aml.dtos.account.AccountResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AccountService {
    Page<AccountResponse> getAllAccounts(Pageable pageable);
}
