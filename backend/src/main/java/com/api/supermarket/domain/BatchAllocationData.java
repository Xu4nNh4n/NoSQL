package com.api.supermarket.domain;

public record BatchAllocationData(Long batchId, int quantity, int returnedQuantity, String status) {}
