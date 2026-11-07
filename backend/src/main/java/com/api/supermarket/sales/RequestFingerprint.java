package com.api.supermarket.sales;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 của nội dung yêu cầu đã chuẩn hóa, để so "gửi lặp" với "dùng trùng requestKey cho yêu cầu khác". */
final class RequestFingerprint {
    private RequestFingerprint() {
    }

    static String of(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
