package com.api.supermarket.security;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.api.supermarket.entity.User;
import com.api.supermarket.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal( HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
    ) throws ServletException, IOException{
        // note: Client gui token theo dang:
        // Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
        String authHeader = request.getHeader("Authorization");

        // note: Neu request khong co token, bo qua filter nay.
        // SecurityConfig se quyet dinh API nay duoc permitAll hay can authenticated.
        if(authHeader == null || !authHeader.startsWith("Bearer ")){
            filterChain.doFilter(request, response);
            return;
        }

        // note: Cat bo chu "Bearer " de lay phan JWT token that.
        String token = authHeader.substring(7);

        // note: Token sai chu ky, het han, hoac format loi thi khong set Authentication.
        if(!jwtService.isTokenValid(token)){
            filterChain.doFilter(request, response);
            return;
        }

        // note: Subject cua token dang la username, duoc set luc generateToken().
        String username = jwtService.extractUsername(token);

        // note: Lay user moi nhat tu DB de dam bao user van ton tai/van active neu can check them.
        User user = userRepository.findByUserName(username).orElse(null);

        // note: Neu user bi xoa hoac bi khoa, khong set Authentication.
        if(user == null || !Boolean.TRUE.equals(user.getIsActive())){
            filterChain.doFilter(request, response);
            return;
        }

        if(SecurityContextHolder.getContext().getAuthentication() == null){
            // note: Tao Authentication de Spring Security hieu request nay da dang nhap.
            // Chuẩn hóa cả ADMIN và ROLE_ADMIN về ROLE_ADMIN.
            String roleName = user.getRole() == null ? "" : user.getRole().getRoleName();
            String authority = roleName == null ? "" : roleName.trim().toUpperCase(Locale.ROOT);
            if (!authority.startsWith("ROLE_")) {
                authority = "ROLE_" + authority;
            }
            List<SimpleGrantedAuthority> authorities = authority.length() > 5
                    ? List.of(new SimpleGrantedAuthority(authority))
                    : List.of();

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    user.getUserName(),
                    null,
                    authorities
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

}
