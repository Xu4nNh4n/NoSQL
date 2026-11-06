package com.api.supermarket.dto.request;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record PosQuoteRequest(@NotEmpty List<@NotNull @Valid OrderItemRequest> items) {}
