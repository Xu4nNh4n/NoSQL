package com.api.supermarket.inventory;

import com.api.supermarket.exception.BadRequestException;

/** Tổng tồn của các lô còn hạn không đủ: từ chối cả đơn, không cấp phát một phần. */
public class InsufficientStockException extends BadRequestException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
