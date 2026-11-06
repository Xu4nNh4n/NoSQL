package com.api.supermarket.exception;

// note: Dùng exception này khi service không tìm thấy dữ liệu cần thao tác.
// Ví dụ:
// - Không tìm thấy category theo id.
// - Không tìm thấy product theo id.
// - Không tìm thấy user theo id.
// GlobalExceptionHandler sẽ bắt exception này và trả về HTTP 404 Not Found.
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message){
        super(message);
    }
}
