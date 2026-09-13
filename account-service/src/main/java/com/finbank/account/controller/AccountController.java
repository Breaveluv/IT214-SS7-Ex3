package com.finbank.account.controller;

import com.finbank.account.dto.AccountBalanceResponse;
import com.finbank.account.dto.AccountResponse;
import com.finbank.account.dto.AmountRequest;
import com.finbank.account.dto.CreateAccountRequest;
import com.finbank.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getAccount(accountNumber));
    }

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<AccountBalanceResponse> getBalance(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getBalance(accountNumber));
    }

    @PutMapping("/{accountNumber}/debit")
    public ResponseEntity<AccountResponse> debit(
            @PathVariable String accountNumber,
            @Valid @RequestBody AmountRequest request) {
        return ResponseEntity.ok(accountService.debit(accountNumber, request.getAmount()));
    }

    @PutMapping("/{accountNumber}/credit")
    public ResponseEntity<AccountResponse> credit(
            @PathVariable String accountNumber,
            @Valid @RequestBody AmountRequest request) {
        return ResponseEntity.ok(accountService.credit(accountNumber, request.getAmount()));
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request));
    }
}
