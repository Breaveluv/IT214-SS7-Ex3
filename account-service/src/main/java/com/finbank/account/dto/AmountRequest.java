package com.finbank.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AmountRequest {

    @NotNull(message = "Số tiền (amount) không được để trống")
    @DecimalMin(value = "0.01", message = "Số tiền (amount) phải lớn hơn 0")
    private BigDecimal amount;
}
