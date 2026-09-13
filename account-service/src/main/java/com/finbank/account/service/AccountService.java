package com.finbank.account.service;

import com.finbank.account.dto.AccountBalanceResponse;
import com.finbank.account.dto.AccountResponse;
import com.finbank.account.dto.CreateAccountRequest;
import com.finbank.account.entity.Account;
import com.finbank.account.exception.AccountNotFoundException;
import com.finbank.account.exception.InsufficientBalanceException;
import com.finbank.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountNumber) {
        Account account = findAccountOrThrow(accountNumber);
        return mapToResponse(account);
    }

    @Transactional(readOnly = true)
    public AccountBalanceResponse getBalance(String accountNumber) {
        Account account = findAccountOrThrow(accountNumber);
        return AccountBalanceResponse.builder()
                .accountNumber(account.getAccountNumber())
                .balance(account.getBalance())
                .build();
    }

    @Transactional
    public AccountResponse debit(String accountNumber, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền trừ phải lớn hơn 0");
        }

        Account account = findAccountOrThrow(accountNumber);

        if (account.getBalance().compareTo(amount) < 0) {
            log.warn("Tài khoản {} không đủ số dư để trừ: hiện có {}, yêu cầu trừ {}",
                    accountNumber, account.getBalance(), amount);
            throw new InsufficientBalanceException("Số dư tài khoản không đủ. Hiện có: "
                    + account.getBalance() + " VND, yêu cầu trừ: " + amount + " VND");
        }

        BigDecimal newBalance = account.getBalance().subtract(amount);
        account.setBalance(newBalance);
        Account saved = accountRepository.save(account);
        log.info("Trừ thành công {} VND từ tài khoản {}. Số dư mới: {}", amount, accountNumber, newBalance);

        return mapToResponse(saved);
    }

    @Transactional
    public AccountResponse credit(String accountNumber, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền cộng phải lớn hơn 0");
        }

        Account account = findAccountOrThrow(accountNumber);

        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);
        Account saved = accountRepository.save(account);
        log.info("Cộng thành công {} VND vào tài khoản {}. Số dư mới: {}", amount, accountNumber, newBalance);

        return mapToResponse(saved);
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        if (accountRepository.existsByAccountNumber(request.getAccountNumber())) {
            throw new IllegalArgumentException("Số tài khoản " + request.getAccountNumber() + " đã tồn tại");
        }

        Account account = Account.builder()
                .accountNumber(request.getAccountNumber())
                .ownerName(request.getOwnerName())
                .balance(request.getInitialBalance() != null ? request.getInitialBalance() : BigDecimal.ZERO)
                .build();

        Account saved = accountRepository.save(account);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private Account findAccountOrThrow(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Không tìm thấy tài khoản với số: " + accountNumber));
    }

    private AccountResponse mapToResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .ownerName(account.getOwnerName())
                .balance(account.getBalance())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
