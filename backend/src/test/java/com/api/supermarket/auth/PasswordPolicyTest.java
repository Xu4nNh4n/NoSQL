package com.api.supermarket.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.api.supermarket.exception.BadRequestException;

class PasswordPolicyTest {

    @Test
    void acceptsNormalPassword() {
        assertDoesNotThrow(() -> PasswordPolicy.check("matkhau123"));
    }

    @Test
    void rejectsShortOrNull() {
        assertThrows(BadRequestException.class, () -> PasswordPolicy.check("1234567"));
        assertThrows(BadRequestException.class, () -> PasswordPolicy.check(null));
    }

    @Test
    void countsUtf8BytesNotCharacters() {
        // "ệ" chiếm 3 byte UTF-8: 25 ký tự = 75 byte > 72 dù chỉ có 25 ký tự.
        assertThrows(BadRequestException.class, () -> PasswordPolicy.check("ệ".repeat(25)));
        assertDoesNotThrow(() -> PasswordPolicy.check("ệ".repeat(24)));
    }
}
