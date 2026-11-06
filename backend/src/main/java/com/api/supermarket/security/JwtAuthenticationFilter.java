package com.api.supermarket.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Đọc header {@code Authorization: Bearer <token>} và đặt {@link JwtPrincipal} vào SecurityContext.
 * Token sai, hết hạn hoặc vai trò không hợp lệ thì request đi tiếp như chưa đăng nhập;
 * SecurityConfig sẽ trả 401 nếu API cần đăng nhập.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.parse(header.substring(7)).ifPresent(principal ->
                    SecurityRoles.normalize(principal.role()).ifPresent(authority -> {
                        var authentication = new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority(authority)));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }));
        }
        filterChain.doFilter(request, response);
    }
}
