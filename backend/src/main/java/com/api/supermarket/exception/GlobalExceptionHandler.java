package com.api.supermarket.exception;

import java.time.Instant;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.api.supermarket.dto.response.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);




    // note: Hàm này bắt lỗi "không tìm thấy dữ liệu".
    // Ví dụ: tìm category/product/user theo id nhưng database không có bản ghi
    // tương ứng.
    // Khi service ném ResourceNotFoundException, Spring sẽ chạy vào đây và trả HTTP
    // 404.
    // Nếu không xử lý riêng, lỗi này dễ bị rơi thành 500 Internal Server Error,
    // không đúng ý nghĩa.
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {
        // Dòng ghi log 404
        log.warn("Resource not found= {}, path= {}", ex.getMessage(), request.getRequestURI());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(
                Instant.now().toString(), // Thời điểm lỗi xảy ra, dùng ISO-8601 để client dễ đọc/log.
                HttpStatus.NOT_FOUND.value(), // Mã HTTP dạng số: 404.
                HttpStatus.NOT_FOUND.getReasonPhrase(), // Tên chuẩn của mã lỗi: "Not Found".
                ex.getMessage(), // Thông báo cụ thể được truyền từ service.
                request.getRequestURI() // Đường dẫn API gây ra lỗi.
        ));
    }

    // note: Hàm này bắt lỗi "request sai nghiệp vụ" hoặc "dữ liệu không hợp lệ".
    // Ví dụ: email đã tồn tại, mã SKU đã tồn tại, roleId không hợp lệ, tên bị
    // trùng.
    // Những lỗi này không phải lỗi server, mà là client gửi dữ liệu chưa đúng, nên
    // trả HTTP 400.
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
            BadRequestException ex,
            HttpServletRequest request) {
        // Dòng ghi log 400
        log.warn("Bad request= {}, path= {}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                Instant.now().toString(), // Thời điểm lỗi xảy ra.
                HttpStatus.BAD_REQUEST.value(), // Mã HTTP dạng số: 400.
                HttpStatus.BAD_REQUEST.getReasonPhrase(), // Tên chuẩn của mã lỗi: "Bad Request".
                ex.getMessage(), // Message cụ thể, ví dụ "Email đã tồn tại".
                request.getRequestURI() // API gây lỗi.
        ));
    }

    // note: Hàm này bắt lỗi validation từ @Valid trong controller.
    // Ví dụ DTO có @NotBlank, @Email, @Min... và client gửi dữ liệu sai.
    // Lỗi MethodArgumentNotValidException xảy ra trước khi vào service, vì Spring
    // validate request body trước.
    // Ở đây mình gom toàn bộ lỗi field thành một chuỗi để client biết field nào
    // sai.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        // Dòng ghi log 400
        log.warn("Validation failed= {}, path= {}", message, request.getRequestURI());
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                Instant.now().toString(), // Thời điểm lỗi xảy ra.
                HttpStatus.BAD_REQUEST.value(), // Validation sai thì trả 400.
                HttpStatus.BAD_REQUEST.getReasonPhrase(), // "Bad Request".
                message, // Danh sách lỗi field, ví dụ "email: không đúng định dạng".
                request.getRequestURI() // API nhận request sai validation.
        ));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(
            UnauthorizedException ex,
            HttpServletRequest request) {
        // Dòng ghi log 401
        log.warn("Unauthorized access= {}, path= {}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiErrorResponse(
                Instant.now().toString(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()));
    }

    // note: Bắt lỗi phân quyền phát sinh từ @PreAuthorize ở Controller/Service.
    // Người dùng đã đăng nhập nhưng Role không đủ quyền thì phải trả 403, không
    // phải 500.
    // JwtAccessDeniedHandler xử lý lỗi trong Security Filter Chain, còn handler này
    // xử lý
    // AccessDeniedException được ném khi Spring gọi method có @PreAuthorize.
    // Ví dụ: tài khoản có ROLE_CASHIER gọi method yêu cầu hasRole('ADMIN').
    // Nếu không có handler riêng này, handleGeneralException(Exception.class) ở
    // cuối file
    // sẽ bắt lỗi và trả 500, khiến client hiểu nhầm rằng server đang bị lỗi.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {
        // Dòng ghi log 403
        log.warn("Access denied= {}, path= {}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiErrorResponse(
                Instant.now().toString(), // Thời điểm xảy ra lỗi phân quyền.
                HttpStatus.FORBIDDEN.value(), // Mã HTTP dạng số: 403.
                HttpStatus.FORBIDDEN.getReasonPhrase(), // Tên chuẩn của mã lỗi: "Forbidden".
                "Bạn không có quyền truy cập tài nguyên này", // Thông báo an toàn gửi cho client.
                request.getRequestURI() // API mà người dùng không đủ quyền gọi.
        ));
    }


    // note: Hàm này là fallback cuối cùng cho các lỗi chưa được xử lý riêng.
    // Ví dụ: lỗi code, lỗi null bất ngờ, lỗi database không dự đoán trước...
    // Với lỗi 500, không nên trả ex.getMessage() cho client vì có thể lộ thông tin
    // nội bộ.
    // Nếu cần debug, nên log lỗi ở server; response cho client chỉ nên là message
    // chung chung.
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleLargeUpload(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(413).body(new ApiErrorResponse(
                Instant.now().toString(), 413, "Payload Too Large", "Ảnh tải lên tối đa 5 MB", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneralException(
            Exception ex,
            HttpServletRequest request) {
        // Dòng ghi log 500
        log.error("Internal server error on path={}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorResponse(
                Instant.now().toString(), // Thời điểm lỗi xảy ra.
                HttpStatus.INTERNAL_SERVER_ERROR.value(), // Mã HTTP dạng số: 500.
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), // "Internal Server Error".
                "Internal server error", // Message chung, không lộ chi tiết lỗi.
                request.getRequestURI() // API gây lỗi.
        ));
    }
}
