package com.api.supermarket.dto.request;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PaymentConfirmationRequest {
    @NotBlank @Size(max = 100)
    private String reference;
    @NotNull @DecimalMin(value = "0", inclusive = false)
    private BigDecimal amount;

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
