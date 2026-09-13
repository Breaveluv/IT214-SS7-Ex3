package com.finbank.transaction.service;

import com.finbank.transaction.dto.TransferRequest;
import com.finbank.transaction.dto.TransferResponse;
import com.finbank.transaction.dto.client.AccountBalanceDto;
import com.finbank.transaction.dto.client.AccountDto;
import com.finbank.transaction.dto.client.AmountDto;
import com.finbank.transaction.entity.Transaction;
import com.finbank.transaction.entity.TransactionStatus;
import com.finbank.transaction.exception.TransferException;
import com.finbank.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final RestTemplate restTemplate;
    private final TransactionRepository transactionRepository;

    private static final String ACCOUNT_SERVICE_URL = "http://ACCOUNT-SERVICE/api/accounts";

    public TransferResponse transfer(TransferRequest request) {
        log.info("Bắt đầu xử lý chuyển tiền: từ {} sang {}, số tiền: {}, nội dung: {}",
                request.getFromAccountNumber(), request.getToAccountNumber(),
                request.getAmount(), request.getDescription());

        // Validate cơ bản
        if (request.getFromAccountNumber().trim().equalsIgnoreCase(request.getToAccountNumber().trim())) {
            recordFailedTransaction(request, "Tài khoản nguồn và tài khoản đích không được trùng nhau");
            throw new TransferException("Tài khoản nguồn và tài khoản đích không được trùng nhau", HttpStatus.BAD_REQUEST);
        }

        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            recordFailedTransaction(request, "Số tiền chuyển phải lớn hơn 0");
            throw new TransferException("Số tiền chuyển phải lớn hơn 0", HttpStatus.BAD_REQUEST);
        }

        // Bước 2: Gọi Account Service để kiểm tra tài khoản nguồn có tồn tại và đủ số dư không
        log.info("Bước 2: Kiểm tra tài khoản nguồn {}", request.getFromAccountNumber());
        AccountBalanceDto fromBalance;
        try {
            String fromBalanceUrl = ACCOUNT_SERVICE_URL + "/" + request.getFromAccountNumber() + "/balance";
            ResponseEntity<AccountBalanceDto> response = restTemplate.getForEntity(fromBalanceUrl, AccountBalanceDto.class);
            fromBalance = response.getBody();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                String reason = "Tài khoản nguồn không tồn tại (" + request.getFromAccountNumber() + ")";
                log.warn(reason);
                recordFailedTransaction(request, reason);
                throw new TransferException(reason, HttpStatus.NOT_FOUND);
            }
            String reason = "Lỗi HTTP từ Account Service khi kiểm tra tài khoản nguồn: " + e.getMessage();
            log.error(reason, e);
            recordFailedTransaction(request, reason);
            throw new TransferException(reason, HttpStatus.valueOf(e.getStatusCode().value()));
        } catch (ResourceAccessException e) {
            String reason = "Không thể kết nối đến Account Service khi kiểm tra tài khoản nguồn";
            log.error(reason, e);
            recordFailedTransaction(request, reason);
            throw new TransferException(reason, HttpStatus.SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            String reason = "Lỗi khi kiểm tra tài khoản nguồn: " + e.getMessage();
            log.error(reason, e);
            recordFailedTransaction(request, reason);
            throw new TransferException(reason, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        if (fromBalance == null || fromBalance.getBalance() == null) {
            String reason = "Không thể lấy thông tin số dư tài khoản nguồn";
            recordFailedTransaction(request, reason);
            throw new TransferException(reason, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        if (fromBalance.getBalance().compareTo(request.getAmount()) < 0) {
            String reason = String.format("Số dư tài khoản nguồn không đủ. Số dư hiện có: %s VND, số tiền yêu cầu: %s VND",
                    fromBalance.getBalance(), request.getAmount());
            log.warn(reason);
            recordFailedTransaction(request, reason);
            throw new TransferException(reason, HttpStatus.BAD_REQUEST);
        }

        // Bước 3: Gọi Account Service để kiểm tra tài khoản đích có tồn tại không
        log.info("Bước 3: Kiểm tra tài khoản đích {}", request.getToAccountNumber());
        try {
            String toAccountUrl = ACCOUNT_SERVICE_URL + "/" + request.getToAccountNumber();
            restTemplate.getForEntity(toAccountUrl, AccountDto.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                String reason = "Tài khoản đích không tồn tại (" + request.getToAccountNumber() + ")";
                log.warn(reason);
                recordFailedTransaction(request, reason);
                throw new TransferException(reason, HttpStatus.NOT_FOUND);
            }
            String reason = "Lỗi HTTP từ Account Service khi kiểm tra tài khoản đích: " + e.getMessage();
            log.error(reason, e);
            recordFailedTransaction(request, reason);
            throw new TransferException(reason, HttpStatus.valueOf(e.getStatusCode().value()));
        } catch (ResourceAccessException e) {
            String reason = "Không thể kết nối đến Account Service khi kiểm tra tài khoản đích";
            log.error(reason, e);
            recordFailedTransaction(request, reason);
            throw new TransferException(reason, HttpStatus.SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            String reason = "Lỗi khi kiểm tra tài khoản đích: " + e.getMessage();
            log.error(reason, e);
            recordFailedTransaction(request, reason);
            throw new TransferException(reason, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // Bước 4: Thực hiện trừ tiền nguồn và cộng tiền đích
        log.info("Bước 4: Trừ tiền tài khoản nguồn {}", request.getFromAccountNumber());
        AmountDto amountPayload = new AmountDto(request.getAmount());
        HttpEntity<AmountDto> entity = new HttpEntity<>(amountPayload);

        boolean debitSuccess = false;
        try {
            String debitUrl = ACCOUNT_SERVICE_URL + "/" + request.getFromAccountNumber() + "/debit";
            restTemplate.exchange(debitUrl, HttpMethod.PUT, entity, AccountDto.class);
            debitSuccess = true;
            log.info("Trừ tiền tài khoản nguồn thành công: -{} VND", request.getAmount());
        } catch (Exception e) {
            String reason = "Trừ tiền tài khoản nguồn thất bại: " + e.getMessage();
            log.error(reason, e);
            recordFailedTransaction(request, reason);
            throw new TransferException(reason, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        log.info("Bước 4: Cộng tiền tài khoản đích {}", request.getToAccountNumber());
        try {
            String creditUrl = ACCOUNT_SERVICE_URL + "/" + request.getToAccountNumber() + "/credit";
            restTemplate.exchange(creditUrl, HttpMethod.PUT, entity, AccountDto.class);
            log.info("Cộng tiền tài khoản đích thành công: +{} VND", request.getAmount());
        } catch (Exception e) {
            log.error("Cộng tiền tài khoản đích thất bại! Tiến hành hoàn lại tiền cho tài khoản nguồn...", e);
            // Giao dịch bù trừ (Compensating transaction) để rollback tiền cho nguồn
            if (debitSuccess) {
                try {
                    String refundUrl = ACCOUNT_SERVICE_URL + "/" + request.getFromAccountNumber() + "/credit";
                    restTemplate.exchange(refundUrl, HttpMethod.PUT, entity, AccountDto.class);
                    log.info("Hoàn tiền thành công cho tài khoản nguồn {}", request.getFromAccountNumber());
                } catch (Exception refundEx) {
                    log.error("NGHIÊM TRỌNG: Hoàn tiền cho tài khoản nguồn thất bại: {}", refundEx.getMessage());
                }
            }
            String reason = "Cộng tiền đích thất bại: " + e.getMessage() + ". Tiền đã được hoàn lại cho nguồn.";
            recordFailedTransaction(request, reason);
            throw new TransferException(reason, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // Bước 5: Lưu bản ghi giao dịch SUCCESS vào database
        log.info("Bước 5: Lưu bản ghi giao dịch SUCCESS vào database");
        Transaction successTx = Transaction.builder()
                .fromAccountNumber(request.getFromAccountNumber())
                .toAccountNumber(request.getToAccountNumber())
                .amount(request.getAmount())
                .description(request.getDescription())
                .status(TransactionStatus.SUCCESS)
                .build();

        Transaction savedTx = transactionRepository.save(successTx);
        log.info("Giao dịch chuyển tiền thành công! Mã giao dịch: {}", savedTx.getId());

        return TransferResponse.builder()
                .transactionId(savedTx.getId())
                .fromAccountNumber(savedTx.getFromAccountNumber())
                .toAccountNumber(savedTx.getToAccountNumber())
                .amount(savedTx.getAmount())
                .description(savedTx.getDescription())
                .status(savedTx.getStatus())
                .message("Chuyển tiền thành công")
                .createdAt(savedTx.getCreatedAt())
                .build();
    }

    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAllByOrderByCreatedAtDesc();
    }

    public Transaction getTransactionById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new TransferException("Không tìm thấy giao dịch ID: " + id, HttpStatus.NOT_FOUND));
    }

    private void recordFailedTransaction(TransferRequest request, String reason) {
        try {
            Transaction failedTx = Transaction.builder()
                    .fromAccountNumber(request.getFromAccountNumber())
                    .toAccountNumber(request.getToAccountNumber())
                    .amount(request.getAmount() != null ? request.getAmount() : BigDecimal.ZERO)
                    .description(request.getDescription())
                    .status(TransactionStatus.FAILED)
                    .failureReason(reason)
                    .build();
            transactionRepository.save(failedTx);
            log.info("Đã lưu bản ghi giao dịch FAILED: lý do [{}]", reason);
        } catch (Exception ex) {
            log.error("Không thể lưu bản ghi giao dịch thất bại: {}", ex.getMessage());
        }
    }
}
