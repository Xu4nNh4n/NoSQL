package com.api.supermarket.dto.response;

import java.time.LocalDate;

public record BatchAllocationResponse(Long batchId, String batchCode, LocalDate expiryDate,
        int quantity, int returnedQuantity, String status) {}
