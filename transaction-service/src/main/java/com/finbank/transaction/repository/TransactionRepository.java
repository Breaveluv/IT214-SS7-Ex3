package com.finbank.transaction.repository;

import com.finbank.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByFromAccountNumberOrToAccountNumberOrderByCreatedAtDesc(
            String fromAccountNumber, String toAccountNumber);
    List<Transaction> findAllByOrderByCreatedAtDesc();
}
