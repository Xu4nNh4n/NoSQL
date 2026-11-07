package com.api.supermarket.common;

/**
 * Cập nhật có điều kiện không khớp vì một giao dịch khác vừa đổi dữ liệu (ví dụ tồn lô đã bị trừ).
 * {@link MongoTx} hủy transaction và chạy lại; hết số lần thử thì báo 409.
 */
public class RetryableConflictException extends ConflictException {
    public RetryableConflictException(String message) {
        super("CONCURRENT_UPDATE", message);
    }
}
