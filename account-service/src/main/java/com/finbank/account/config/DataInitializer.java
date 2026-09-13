package com.finbank.account.config;

import com.finbank.account.entity.Account;
import com.finbank.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AccountRepository accountRepository;

    @Override
    public void run(String... args) {
        if (!accountRepository.existsByAccountNumber("1001")) {
            Account acc1 = Account.builder()
                    .accountNumber("1001")
                    .ownerName("Nguyen Van A")
                    .balance(new BigDecimal("10000000.00"))
                    .build();
            accountRepository.save(acc1);
            log.info("Initialized test account 1001 with balance: 10,000,000 VND");
        }

        if (!accountRepository.existsByAccountNumber("1002")) {
            Account acc2 = Account.builder()
                    .accountNumber("1002")
                    .ownerName("Tran Thi B")
                    .balance(new BigDecimal("5000000.00"))
                    .build();
            accountRepository.save(acc2);
            log.info("Initialized test account 1002 with balance: 5,000,000 VND");
        }
    }
}
