package com.api.supermarket.config;

import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // Chạy đầu tiên trước tất cả các Filter khácc
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-ID";
    private static final String TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // 1. Kiểm tra xem Client có gửi sẵn Header X-Trace-ID không, nếu không thì tự
        // sinh UUID mới
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
        }

        // 2. Đưa traceId vào MD để SLF4J tự động chèn vào mọi câu log
        MDC.put(TRACE_ID_KEY, traceId);

        // 3. Trả ngược traceId về Header cho Client/Frontend biết (để báo lỗi khi cần)
        response.setHeader(TRACE_ID_HEADER, traceId);

        // 4. Thực hiện xử lý yêu cầu (Gọi controller)
        try {
            // Cho request đi tiếp vào Controller/Service
            filterChain.doFilter(request, response);
        } finally {
            // Cực kỳ quan trọng: Xóa MDC khi request kết thúc tránh rò rỉ bộ nhớ trong
            // ThreadPool
            MDC.clear();
        }
    }
}
