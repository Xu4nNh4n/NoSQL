package com.api.supermarket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import com.api.supermarket.security.JwtAuthenticationFilter;
import com.api.supermarket.security.JwtAuthenticationEntryPoint;
import com.api.supermarket.security.JwtAccessDeniedHandler;
import com.api.supermarket.security.SecurityRoles;
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint, JwtAccessDeniedHandler jwtAccessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
    }

    // note: PasswordEncoder la bean dung chung de ma hoa/kiem tra password bang BCrypt.
    // AuthServiceImpl se inject bean nay thay vi tu new BCryptPasswordEncoder().
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception{
        return http
                // note: API dang dung JWT stateless, nen tat CSRF cho cac request REST.
                .csrf(csrf -> csrf.disable())
                // note: STATELESS nghia la backend khong luu session dang nhap tren server.
                // Moi request can tu mang token trong header Authorization.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                    .accessDeniedHandler(jwtAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/product-images/*").permitAll()
                        // note: Mo Swagger/OpenAPI de xem tai lieu API va import vao Bruno/Postman.
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout"
                            ).permitAll()
                        .anyRequest().hasAnyRole(SecurityRoles.ADMIN, SecurityRoles.WAREHOUSE, SecurityRoles.STAFF))
                // note: Chay JwtAuthenticationFilter truoc filter login mac dinh cua Spring Security.
                // Neu token hop le, filter se set Authentication vao SecurityContext.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
