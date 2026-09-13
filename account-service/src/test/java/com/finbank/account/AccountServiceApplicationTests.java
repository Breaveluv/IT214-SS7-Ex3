package com.finbank.account;

import com.finbank.account.dto.AccountBalanceResponse;
import com.finbank.account.dto.AccountResponse;
import com.finbank.account.dto.AmountRequest;
import com.finbank.account.exception.InsufficientBalanceException;
import com.finbank.account.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AccountServiceApplicationTests {

    @Autowired
    private AccountService accountService;

    @Test
    void testInitialAccountsExist() {
        AccountResponse acc1001 = accountService.getAccount("1001");
        assertNotNull(acc1001);
        assertEquals("Nguyen Van A", acc1001.getOwnerName());

        AccountResponse acc1002 = accountService.getAccount("1002");
        assertNotNull(acc1002);
        assertEquals("Tran Thi B", acc1002.getOwnerName());
    }

    @Test
    void testGetBalance() {
        AccountBalanceResponse balanceResponse = accountService.getBalance("1001");
        assertNotNull(balanceResponse);
        assertEquals("1001", balanceResponse.getAccountNumber());
        assertTrue(balanceResponse.getBalance().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testDebitInsufficientBalance() {
        assertThrows(InsufficientBalanceException.class, () -> {
            accountService.debit("1001", new BigDecimal("999999999.00"));
        });
    }

    @Test
    void testDebitAndCreditSuccess() {
        AccountBalanceResponse before = accountService.getBalance("1002");
        BigDecimal initialBalance = before.getBalance();

        // Debit 500,000
        AccountResponse debited = accountService.debit("1002", new BigDecimal("500000.00"));
        assertEquals(initialBalance.subtract(new BigDecimal("500000.00")), debited.getBalance());

        // Credit 500,000 back
        AccountResponse credited = accountService.credit("1002", new BigDecimal("500000.00"));
        assertEquals(initialBalance, credited.getBalance());
    }
}
