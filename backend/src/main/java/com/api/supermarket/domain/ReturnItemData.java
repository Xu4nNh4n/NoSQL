package com.api.supermarket.domain;

public record ReturnItemData(Long orderItemId, Long batchId, int quantity, boolean restock, String reason) {}
