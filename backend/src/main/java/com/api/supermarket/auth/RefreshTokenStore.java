package com.api.supermarket.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.api.supermarket.common.RedisSafe;

/**
 * Refresh token trên Redis (AP-02). Chỉ lưu bản băm SHA-256, có TTL, mỗi token dùng một lần:
 * làm mới thì {@code GETDEL} token cũ rồi cấp token mới (xoay vòng).
 * <pre>
 * auth:refresh:{sha256}     string → userId          TTL 7 ngày
 * auth:user-tokens:{userId} set các sha256 của user  TTL 7 ngày (để thu hồi hết khi khóa/đổi mật khẩu)
 * </pre>
 */
@Component
public class RefreshTokenStore {
    static final String TOKEN_PREFIX = "auth:refresh:";
    static final String USER_PREFIX = "auth:user-tokens:";

    private final RedisSafe redis;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenStore(RedisSafe redis, @Value("${jwt.refresh-days:7}") long refreshDays) {
        this.redis = redis;
        this.ttl = Duration.ofDays(refreshDays);
    }

    /** @return token gốc cho client, hoặc rỗng khi Redis không khả dụng */
    public Optional<String> issue(String userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = hash(raw);
        if (!redis.set(TOKEN_PREFIX + hash, userId, ttl)) {
            return Optional.empty();
        }
        redis.sAdd(USER_PREFIX + userId, ttl, hash);
        return Optional.of(raw);
    }

    /** Dùng token một lần. @return userId nếu token còn hiệu lực */
    public Optional<String> consume(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String hash = hash(raw);
        Optional<String> userId = redis.getAndDelete(TOKEN_PREFIX + hash);
        userId.ifPresent(id -> redis.sRem(USER_PREFIX + id, hash));
        return userId;
    }

    public void revoke(String raw) {
        consume(raw);
    }

    /** Thu hồi mọi phiên của người dùng (khóa tài khoản, đổi mật khẩu, đổi vai trò). */
    public void revokeAll(String userId) {
        List<String> keys = new ArrayList<>();
        for (String hash : redis.sMembers(USER_PREFIX + userId)) {
            keys.add(TOKEN_PREFIX + hash);
        }
        keys.add(USER_PREFIX + userId);
        redis.delete(keys.toArray(String[]::new));
    }

    public Duration ttl() {
        return ttl;
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
