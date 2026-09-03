package com.tss.aml.repositories;

import com.tss.aml.entities.tenant.Account;
import com.tss.aml.entities.tenant.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID> {
    @Query("""
        SELECT COUNT(t) FROM FinancialTransaction t 
        WHERE t.originatorAccount = :account 
        AND t.txnTimestamp >= :startDate 
        AND t.txnTimestamp < :endDate"""
    )
    Integer countTransactionsInWindow(
            @Param("account") Account account,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("""
        SELECT SUM(t.amount) FROM FinancialTransaction t 
        WHERE t.originatorAccount = :account 
        AND t.txnTimestamp >= :startDate 
        AND t.txnTimestamp < :endDate"""
    )
    BigDecimal sumTransactionAmountsInWindow(
            @Param("account") Account account,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("""
        SELECT COUNT(t) FROM FinancialTransaction t 
        WHERE t.originatorAccount = :account 
        AND t.txnTimestamp < :beforeDate 
        AND t.txnTimestamp >= :startDate"""
    )
    Integer countPriorTransactions(
            @Param("account") Account account,
            @Param("startDate") LocalDateTime startDate,
            @Param("beforeDate") LocalDateTime beforeDate
    );

    @Query("""
        SELECT SUM(t.amount) FROM FinancialTransaction t 
        WHERE t.originatorAccount = :account 
        AND t.direction = :direction 
        AND t.txnTimestamp >= :startDate 
        AND t.txnTimestamp < :endDate"""
    )
    BigDecimal sumAmountByDirectionInWindow(
            @Param("account") Account account,
            @Param("direction") com.tss.aml.enums.TransactionDirection direction,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}



