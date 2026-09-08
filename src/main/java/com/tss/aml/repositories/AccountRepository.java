package com.tss.aml.repositories;

import com.tss.aml.entities.tenant.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByAccountNumber(String accountNumber);

    java.util.List<Account> findByAccountHolderNameIgnoreCaseAndAccountIdNot(String accountHolderName, UUID accountId);
}
