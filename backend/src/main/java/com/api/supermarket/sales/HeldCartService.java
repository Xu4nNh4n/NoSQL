package com.api.supermarket.sales;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.DatastoreUnavailableException;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.sales.QuoteService.QuoteItem;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Đơn treo tại quầy (FR-08, AP-18) chỉ nằm trên Redis:
 * <pre>
 * pos:held:{cashierId}:{holdId}   string JSON giỏ           TTL 30 phút
 * pos:held-index:{cashierId}      sorted set holdId theo giờ TTL 30 phút
 * </pre>
 * Mở lại dùng Lua script GET + DEL + ZREM trong một lệnh nguyên tử, nên hai máy không mở được cùng một đơn.
 * Đơn treo không giữ tồn kho; giá được tính lại khi mở. Redis lỗi thì báo 503, việc bán vẫn chạy.
 */
@Service
public class HeldCartService {
    static final Duration TTL = Duration.ofMinutes(30);
    static final int MAX_PER_CASHIER = 10;
    static final RedisScript<String> TAKE = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]) "
                    + "if v then redis.call('DEL', KEYS[1]) redis.call('ZREM', KEYS[2], ARGV[1]) end "
                    + "return v",
            String.class);

    public record HoldRequest(@Size(max = 100) String note, String customerId,
            @NotEmpty @Size(max = 100) List<@Valid QuoteItem> items) {
    }

    public record HeldCart(String holdId, String note, String customerId, List<QuoteItem> items, Instant heldAt,
            Instant expiresAt) {
    }

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final AppTime time;

    public HeldCartService(StringRedisTemplate redis, JsonMapper json, AppTime time) {
        this.redis = redis;
        this.json = json;
        this.time = time;
    }

    static String key(String cashierId, String holdId) {
        return "pos:held:" + cashierId + ":" + holdId;
    }

    static String index(String cashierId) {
        return "pos:held-index:" + cashierId;
    }

    public HeldCart hold(String cashierId, HoldRequest req) {
        try {
            if (list(cashierId).size() >= MAX_PER_CASHIER) {
                throw new BadRequestException("Tối đa " + MAX_PER_CASHIER + " đơn treo mỗi thu ngân");
            }
            Instant now = time.now();
            HeldCart cart = new HeldCart(UUID.randomUUID().toString(), req.note(), req.customerId(), req.items(), now,
                    now.plus(TTL));
            redis.opsForValue().set(key(cashierId, cart.holdId()), json.writeValueAsString(cart), TTL);
            redis.opsForZSet().add(index(cashierId), cart.holdId(), now.toEpochMilli());
            redis.expire(index(cashierId), TTL);
            return cart;
        } catch (DataAccessException ex) {
            throw new DatastoreUnavailableException("redis", ex);
        }
    }

    /** Liệt kê đơn treo còn hạn, mới nhất trước; dọn khỏi chỉ mục các đơn đã hết hạn. */
    public List<HeldCart> list(String cashierId) {
        try {
            Set<String> ids = redis.opsForZSet().reverseRange(index(cashierId), 0, -1);
            List<HeldCart> result = new ArrayList<>();
            if (ids == null || ids.isEmpty()) {
                return result;
            }
            List<String> ordered = new ArrayList<>(ids);
            List<String> values = redis.opsForValue().multiGet(ordered.stream().map(id -> key(cashierId, id)).toList());
            for (int i = 0; i < ordered.size(); i++) {
                String v = values == null ? null : values.get(i);
                if (v == null) {
                    redis.opsForZSet().remove(index(cashierId), ordered.get(i));
                } else {
                    result.add(parse(v));
                }
            }
            return result;
        } catch (DataAccessException ex) {
            throw new DatastoreUnavailableException("redis", ex);
        }
    }

    /** Mở lại: lấy giỏ và xóa khỏi Redis trong một lệnh. */
    public HeldCart resume(String cashierId, String holdId) {
        try {
            String v = redis.execute(TAKE, List.of(key(cashierId, holdId), index(cashierId)), holdId);
            if (v == null) {
                throw new ResourceNotFoundException("Đơn treo đã hết hạn hoặc đã được mở");
            }
            return parse(v);
        } catch (DataAccessException ex) {
            throw new DatastoreUnavailableException("redis", ex);
        }
    }

    public void delete(String cashierId, String holdId) {
        try {
            redis.delete(key(cashierId, holdId));
            redis.opsForZSet().remove(index(cashierId), holdId);
        } catch (DataAccessException ex) {
            throw new DatastoreUnavailableException("redis", ex);
        }
    }

    private HeldCart parse(String v) {
        try {
            return json.readValue(v, HeldCart.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Đơn treo hỏng dữ liệu", ex);
        }
    }
}
