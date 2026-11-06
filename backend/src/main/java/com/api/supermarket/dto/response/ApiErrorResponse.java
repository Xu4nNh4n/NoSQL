package com.api.supermarket.dto.response;

// Class này đại diện cho phản hồi lỗi API, có thể được sử dụng để gửi thông tin lỗi từ các controller đến client.
public record ApiErrorResponse(
    String timestamp, // thời điểm lỗi
    int status, // mã lỗi HTTP, ví dụ 401, 403
    String error, // tên lỗi, ví dụ Unauthorized, Forbidden
    String message, // thông điệp lỗi chi tiết
    String path // API path bị lỗi
) {

}
