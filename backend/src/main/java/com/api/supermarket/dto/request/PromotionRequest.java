package com.api.supermarket.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.validation.constraints.*;

public record PromotionRequest(@NotNull @Positive Long productId,
        @NotBlank @Size(max = 150) String name,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100")
        @Digits(integer = 3, fraction = 2) BigDecimal percent,
        @NotNull LocalDateTime startsAt, @NotNull LocalDateTime endsAt, @NotNull Boolean active) {}
