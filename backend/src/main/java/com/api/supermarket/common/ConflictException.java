package com.api.supermarket.common;

/** Xung đột trạng thái (ghi đè đồng thời, gửi lặp khác nội dung...). Trả HTTP 409. */
public class ConflictException extends RuntimeException {
    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
