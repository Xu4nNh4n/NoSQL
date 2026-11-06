package com.api.supermarket.exception;

// note: Dùng exception này khi request sai nghiệp vụ hoặc dữ liệu không hợp lệ.
// Ví dụ:
// - Email đã tồn tại.
// - Mã SKU đã tồn tại.
// - roleId không tồn tại.
// - Tên danh mục bị trùng.
// GlobalExceptionHandler sẽ bắt exception này và trả về HTTP 400 Bad Request.
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message){
        super(message);
    }
}
