package com.finbank.transaction;

import com.finbank.transaction.dto.TransferRequest;
import com.finbank.transaction.dto.TransferResponse;
import com.finbank.transaction.dto.client.AccountBalanceDto;
import com.finbank.transaction.dto.client.AccountDto;
import com.finbank.transaction.dto.client.AmountDto;
import com.finbank.transaction.entity.Transaction;
import com.finbank.transaction.entity.TransactionStatus;
import com.finbank.transaction.exception.TransferException;
import com.finbank.transaction.repository.TransactionRepository;
import com.finbank.transaction.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class TransactionServiceApplicationTests {

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransactionRepository transactionRepository;

    @MockBean
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
    }

    @Test
    void testTransferSuccess() {
        // Mock Step 2: get balance of 1001 -> 10,000,000
        AccountBalanceDto balance1001 = new AccountBalanceDto("1001", new BigDecimal("10000000.00"));
        when(restTemplate.getForEntity("http://ACCOUNT-SERVICE/api/accounts/1001/balance", AccountBalanceDto.class))
                .thenReturn(new ResponseEntity<>(balance1001, HttpStatus.OK));

        // Mock Step 3: check toAccount 1002
        AccountDto acc1002 = new AccountDto(2L, "1002", "Tran Thi B", new BigDecimal("5000000.00"));
        when(restTemplate.getForEntity("http://ACCOUNT-SERVICE/api/accounts/1002", AccountDto.class))
                .thenReturn(new ResponseEntity<>(acc1002, HttpStatus.OK));

        // Mock Step 4: debit 1001 & credit 1002
        when(restTemplate.exchange(eq("http://ACCOUNT-SERVICE/api/accounts/1001/debit"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(AccountDto.class)))
                .thenReturn(new ResponseEntity<>(new AccountDto(1L, "1001", "Nguyen Van A", new BigDecimal("8000000.00")), HttpStatus.OK));

        when(restTemplate.exchange(eq("http://ACCOUNT-SERVICE/api/accounts/1002/credit"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(AccountDto.class)))
                .thenReturn(new ResponseEntity<>(new AccountDto(2L, "1002", "Tran Thi B", new BigDecimal("7000000.00")), HttpStatus.OK));

        TransferRequest request = TransferRequest.builder()
                .fromAccountNumber("1001")
                .toAccountNumber("1002")
                .amount(new BigDecimal("2000000.00"))
                .description("Chuyen tien thanh toan hoa don")
                .build();

        TransferResponse response = transferService.transfer(request);

        assertNotNull(response);
        assertEquals(TransactionStatus.SUCCESS, response.getStatus());
        assertEquals(new BigDecimal("2000000.00"), response.getAmount());

        List<Transaction> transactions = transactionRepository.findAll();
        assertEquals(1, transactions.size());
        assertEquals(TransactionStatus.SUCCESS, transactions.get(0).getStatus());
    }

    @Test
    void testTransferInsufficientBalance() {
        // Mock Step 2: get balance of 1001 -> 10,000,000, but request is 100,000,000
        AccountBalanceDto balance1001 = new AccountBalanceDto("1001", new BigDecimal("10000000.00"));
        when(restTemplate.getForEntity("http://ACCOUNT-SERVICE/api/accounts/1001/balance", AccountBalanceDto.class))
                .thenReturn(new ResponseEntity<>(balance1001, HttpStatus.OK));

        TransferRequest request = TransferRequest.builder()
                .fromAccountNumber("1001")
                .toAccountNumber("1002")
                .amount(new BigDecimal("100000000.00"))
                .description("Chuyen tien vuot han muc")
                .build();

        assertThrows(TransferException.class, () -> transferService.transfer(request));

        List<Transaction> transactions = transactionRepository.findAll();
        assertEquals(1, transactions.size());
        assertEquals(TransactionStatus.FAILED, transactions.get(0).getStatus());
        assertTrue(transactions.get(0).getFailureReason().contains("không đủ"));
    }

    @Test
    void testTransferToNonExistentAccount() {
        // Mock Step 2: balance 1001 is ok
        AccountBalanceDto balance1001 = new AccountBalanceDto("1001", new BigDecimal("10000000.00"));
        when(restTemplate.getForEntity("http://ACCOUNT-SERVICE/api/accounts/1001/balance", AccountBalanceDto.class))
                .thenReturn(new ResponseEntity<>(balance1001, HttpStatus.OK));

        // Mock Step 3: toAccount 9999 throws NotFound
        when(restTemplate.getForEntity("http://ACCOUNT-SERVICE/api/accounts/9999", AccountDto.class))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found"));

        TransferRequest request = TransferRequest.builder()
                .fromAccountNumber("1001")
                .toAccountNumber("9999")
                .amount(new BigDecimal("2000000.00"))
                .description("Chuyen tien tai khoan ao")
                .build();

        assertThrows(TransferException.class, () -> transferService.transfer(request));

        List<Transaction> transactions = transactionRepository.findAll();
        assertEquals(1, transactions.size());
        assertEquals(TransactionStatus.FAILED, transactions.get(0).getStatus());
        assertTrue(transactions.get(0).getFailureReason().contains("Tài khoản đích không tồn tại"));
    }
}
