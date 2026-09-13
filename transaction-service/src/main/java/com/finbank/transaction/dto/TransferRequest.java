package com.finbank.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferRequest {

    @NotBlank(message = "Số tài khoản nguồn không được để trống")
    private String fromAccountNumber;

    @NotBlank(message = "Số tài khoản đích không được để trống")
    private String toAccountNumber;

    @NotNull(message = "Số tiền không được để trống")
    @DecimalMin(value = "0.01", message = "Số tiền chuyển phải lớn hơn 0")
    private BigDecimal amount;

    private String description;
}
