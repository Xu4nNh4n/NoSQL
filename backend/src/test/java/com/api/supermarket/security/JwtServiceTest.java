package com.api.supermarket.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void roundTripKeepsClaims() {
        JwtService service = new JwtService(SECRET, 900_000);
        JwtPrincipal principal = new JwtPrincipal("u1", "thungan01", "STAFF");

        assertEquals(Optional.of(principal), service.parse(service.generateToken(principal)));
    }

    @Test
    void expiredTokenIsEmpty() {
        JwtService service = new JwtService(SECRET, -1);
        String token = service.generateToken(new JwtPrincipal("u1", "admin", "ADMIN"));

        assertTrue(service.parse(token).isEmpty());
    }

    @Test
    void tamperedTokenIsEmpty() {
        JwtService service = new JwtService(SECRET, 900_000);
        String token = service.generateToken(new JwtPrincipal("u1", "admin", "ADMIN"));
        // Đổi ký tự ĐẦU của chữ ký: ký tự cuối base64url còn bit đệm nên đổi nó có thể không làm đổi chữ ký.
        int sigStart = token.lastIndexOf('.') + 1;
        char first = token.charAt(sigStart);
        String tampered = token.substring(0, sigStart) + (first == 'A' ? 'B' : 'A') + token.substring(sigStart + 1);

        assertTrue(service.parse(tampered).isEmpty());
    }

    @Test
    void garbageTokenIsEmpty() {
        JwtService service = new JwtService(SECRET, 900_000);

        assertTrue(service.parse("not-a-jwt").isEmpty());
        assertTrue(service.parse(null).isEmpty());
    }

    @Test
    void shortSecretFailsFast() {
        assertThrows(IllegalStateException.class, () -> new JwtService("short", 1000));
    }

    @Test
    void normalizesRoles() {
        assertEquals(Optional.of("ROLE_ADMIN"), SecurityRoles.normalize("admin"));
        assertEquals(Optional.of("ROLE_STAFF"), SecurityRoles.normalize("ROLE_STAFF"));
        assertEquals(Optional.of("ROLE_WAREHOUSE"), SecurityRoles.normalize(" warehouse "));
        assertTrue(SecurityRoles.normalize("MANAGER").isEmpty());
        assertTrue(SecurityRoles.normalize(null).isEmpty());
    }
}
