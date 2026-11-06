package com.api.supermarket.security;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.api.supermarket.dto.response.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

//Class này đại diện cho lỗi 403 
//Forbidden khi người dùng không có quyền truy cập vào tài nguyên bảo vệ.
// Nó triển khai AccessDeniedHandler của Spring Security
// và được sử dụng để gửi phản hồi lỗi khi người dùng không có quyền truy cập.
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler{
    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
        HttpServletRequest request,
        HttpServletResponse response,
        AccessDeniedException accessDeniedException
    ) throws IOException, ServletException{
        ApiErrorResponse errorResponse = new ApiErrorResponse(
            Instant.now().toString(),
            HttpStatus.FORBIDDEN.value(),
            HttpStatus.FORBIDDEN.getReasonPhrase(),
            "Truy cập bị từ chối: " + accessDeniedException.getMessage(),
            request.getRequestURI()
        );
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
