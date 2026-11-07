package com.api.supermarket.common;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

/**
 * Mọi lệnh Redis đi qua lớp này. Redis chỉ giữ dữ liệu tạm, nên khi Redis lỗi
 * thì ghi log và trả giá trị an toàn, không làm hỏng request (NFR-09).
 */
@Component
public class RedisSafe {
    private static final Logger log = LoggerFactory.getLogger(RedisSafe.class);

    public record ZEntry(String member, double score) {
    }

    private final StringRedisTemplate redis;

    public RedisSafe(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Optional<String> get(String key) {
        return call("GET " + key, () -> Optional.ofNullable(redis.opsForValue().get(key)), Optional.empty());
    }

    public boolean set(String key, String value, Duration ttl) {
        return call("SET " + key, () -> {
            redis.opsForValue().set(key, value, ttl);
            return true;
        }, false);
    }

    /** Lấy và xóa trong một lệnh (GETDEL): giá trị chỉ được dùng một lần. */
    public Optional<String> getAndDelete(String key) {
        return call("GETDEL " + key, () -> Optional.ofNullable(redis.opsForValue().getAndDelete(key)), Optional.empty());
    }

    public boolean sAdd(String key, Duration ttl, String... members) {
        return call("SADD " + key, () -> {
            redis.opsForSet().add(key, members);
            redis.expire(key, ttl);
            return true;
        }, false);
    }

    public Set<String> sMembers(String key) {
        return call("SMEMBERS " + key, () -> {
            Set<String> members = redis.opsForSet().members(key);
            return members == null ? Set.<String>of() : members;
        }, Set.of());
    }

    public boolean sRem(String key, String... members) {
        return call("SREM " + key, () -> {
            redis.opsForSet().remove(key, (Object[]) members);
            return true;
        }, false);
    }

    public boolean delete(String... keys) {
        return call("DEL", () -> {
            redis.delete(List.of(keys));
            return true;
        }, false);
    }

    /** Cộng điểm cho member; đặt TTL khi key chưa có hạn (key mới). */
    public boolean zIncrBy(String key, String member, double delta, Duration ttlIfNew) {
        return call("ZINCRBY " + key, () -> {
            redis.opsForZSet().incrementScore(key, member, delta);
            Long ttl = redis.getExpire(key);
            if (ttl != null && ttl < 0) {
                redis.expire(key, ttlIfNew);
            }
            return true;
        }, false);
    }

    public List<ZEntry> zRevRangeWithScores(String key, int limit) {
        return call("ZREVRANGE " + key, () -> {
            Set<TypedTuple<String>> tuples = redis.opsForZSet().reverseRangeWithScores(key, 0, limit - 1L);
            List<ZEntry> result = new ArrayList<>();
            if (tuples != null) {
                for (TypedTuple<String> t : tuples) {
                    result.add(new ZEntry(t.getValue(), t.getScore() == null ? 0 : t.getScore()));
                }
            }
            return result;
        }, List.of());
    }

    public boolean publish(String channel, String message) {
        return call("PUBLISH " + channel, () -> {
            redis.convertAndSend(channel, message);
            return true;
        }, false);
    }

    public boolean ping() {
        return call("PING", () -> {
            String pong = redis.execute(connection -> connection.ping(), true);
            return "PONG".equalsIgnoreCase(pong);
        }, false);
    }

    private <T> T call(String what, Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (RuntimeException ex) {
            log.warn("Redis không khả dụng ({}): {}", what, ex.toString());
            return fallback;
        }
    }
}
