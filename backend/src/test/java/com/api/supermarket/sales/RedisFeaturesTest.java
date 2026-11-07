package com.api.supermarket.sales;

import static com.api.supermarket.sales.SalesTestSupport.TIME;
import static com.api.supermarket.sales.SalesTestSupport.TODAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.common.DatastoreUnavailableException;
import com.api.supermarket.common.RedisSafe;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.sales.HeldCartService.HeldCart;
import com.api.supermarket.sales.HeldCartService.HoldRequest;
import com.api.supermarket.sales.QuoteService.QuoteItem;

import tools.jackson.databind.json.JsonMapper;

/** Đơn treo và top bán chạy trên Redis. */
class RedisFeaturesTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @SuppressWarnings("unchecked")
    private StringRedisTemplate template(ValueOperations<String, String> values, ZSetOperations<String, String> zset) {
        StringRedisTemplate t = mock(StringRedisTemplate.class);
        when(t.opsForValue()).thenReturn(values);
        when(t.opsForZSet()).thenReturn(zset);
        return t;
    }

    @Test
    @SuppressWarnings("unchecked")
    void holdStoresCartWithTtlAndIndexesIt() {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        StringRedisTemplate t = template(values, zset);
        HeldCartService service = new HeldCartService(t, json, TIME);

        HeldCart cart = service.hold("c1", new HoldRequest("Chị Lan", List.of(new QuoteItem("milk", 2))));
        verify(values).set(eq(HeldCartService.key("c1", cart.holdId())), anyString(), eq(Duration.ofMinutes(30)));
        verify(zset).add(eq("pos:held-index:c1"), eq(cart.holdId()), anyDouble());
        assertEquals(TIME.now().plus(Duration.ofMinutes(30)), cart.expiresAt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listDropsExpiredEntriesFromIndex() {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        StringRedisTemplate t = template(values, zset);
        HeldCart alive = new HeldCart("h1", null, List.of(new QuoteItem("milk", 1)), TIME.now(), TIME.now());
        when(zset.reverseRange("pos:held-index:c1", 0, -1)).thenReturn(new LinkedHashSet<>(List.of("h1", "h2")));
        when(values.multiGet(anyList())).thenReturn(java.util.Arrays.asList(json.writeValueAsString(alive), null));

        List<HeldCart> list = new HeldCartService(t, json, TIME).list("c1");
        assertEquals(List.of(alive), list);
        verify(zset).remove("pos:held-index:c1", "h2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void resumeUsesAtomicScriptAndReportsMissing() {
        StringRedisTemplate t = template(mock(ValueOperations.class), mock(ZSetOperations.class));
        when(t.execute(eq(HeldCartService.TAKE), anyList(), any())).thenReturn(null);
        assertThrows(ResourceNotFoundException.class, () -> new HeldCartService(t, json, TIME).resume("c1", "h1"));
        verify(t).execute(HeldCartService.TAKE, List.of("pos:held:c1:h1", "pos:held-index:c1"), "h1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisDownIs503ForHeldCarts() {
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        StringRedisTemplate t = template(mock(ValueOperations.class), zset);
        when(zset.reverseRange(anyString(), any(Long.class), any(Long.class)))
                .thenThrow(new RedisConnectionFailureException("down"));
        assertThrows(DatastoreUnavailableException.class, () -> new HeldCartService(t, json, TIME).list("c1"));
    }

    @Test
    void topSalesSkipsGiftLinesAndUsesDailyKey() {
        RedisSafe redis = mock(RedisSafe.class);
        TopSalesService top = new TopSalesService(redis, mock(ProductRepository.class), TIME);
        SalesOrder o = SalesTestSupport.paidOrder();
        SalesOrder.OrderLine gift = new SalesOrder.OrderLine();
        gift.setProductId("glass");
        gift.setQuantity(1);
        gift.setGift(true);
        o.getItems().add(gift);
        top.record(o);
        verify(redis).zIncrBy("top:sales:20261107", "milk", 3, TopSalesService.TTL);
        verify(redis, never()).zIncrBy(eq("top:sales:20261107"), eq("glass"), anyDouble(), any());
        assertEquals("top:sales:20261107", TopSalesService.key(TODAY));
    }

    @Test
    void topSalesReportsUnavailableWhenRedisDown() {
        RedisSafe redis = mock(RedisSafe.class);
        when(redis.ping()).thenReturn(false);
        TopSalesService.TopSales res = new TopSalesService(redis, mock(ProductRepository.class), TIME).today(10);
        assertFalse(res.available());
        assertEquals(Set.of(), Set.copyOf(res.items()));
    }
}
