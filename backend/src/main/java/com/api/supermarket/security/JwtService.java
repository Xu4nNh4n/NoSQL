package com.api.supermarket.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.api.supermarket.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    // note: Ham nay duoc goi sau khi login thanh cong.
    // No tao JWT gom username, userId, role, thoi diem tao va thoi diem het han.
    public String generateToken(User user){
        Date now = new Date();

        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
        // note: subject la danh tinh chinh cua token; o day minh dung username.
        .subject(user.getUserName())
        // note: claim la thong tin phu minh muon gui kem trong token.
        .claim("userId", user.getUserId())
        .claim("role", user.getRole().getRoleName())
        .issuedAt(now)
        .expiration(expiryDate)
        // note: Ky token bang secret key. Neu token bi sua, chu ky se khong hop le.
        .signWith(getSigningKey())
        .compact();
    }

    // note: Tao khoa ky tu jwt.secret trong application.properties.
    // Secret phai du dai cho HS256; project that nen dua secret vao bien moi truong.
    private SecretKey getSigningKey(){
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // note: Lay username tu subject cua token de filter tim user trong database.
    public String extractUsername(String token){
        return extractAllClaims(token).getSubject();
    }

    // note: Validate token bang cach parse va verify chu ky/han su dung.
    // Neu token sai, het han, hoac format loi thi jjwt se throw exception.
    public boolean isTokenValid(String token){
        try{
            extractAllClaims(token);
            return true;
        }
        catch (Exception e){
            return false;
        }
    }

    // note: Parse token de lay payload claims.
    // verifyWith(getSigningKey()) dam bao token dung chu ky cua backend minh.
    private Claims extractAllClaims(String token){
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
