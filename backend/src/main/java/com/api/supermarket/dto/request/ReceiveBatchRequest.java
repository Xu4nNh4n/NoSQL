package com.api.supermarket.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.*;

public record ReceiveBatchRequest(
        @NotNull @Positive Long productId,
        @NotNull @Positive Long supplierId,
        @Size(max = 100) String batchCode,
        @Positive int quantity,
        @NotNull @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal unitCost,
        LocalDate manufacturingDate, LocalDate expiryDate) {}
