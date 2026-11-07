package com.api.supermarket.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

class RedisSafeTest {
    private StringRedisTemplate template;
    private ValueOperations<String, String> values;
    private ZSetOperations<String, String> zset;
    private RedisSafe redis;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        template = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        zset = mock(ZSetOperations.class);
        when(template.opsForValue()).thenReturn(values);
        when(template.opsForZSet()).thenReturn(zset);
        redis = new RedisSafe(template);
    }

    private static RedisConnectionFailureException down() {
        return new RedisConnectionFailureException("down");
    }

    @Test
    void failuresReturnSafeValues() {
        when(values.get(anyString())).thenThrow(down());
        org.mockito.Mockito.doThrow(down()).when(values).set(anyString(), anyString(), any(Duration.class));
        when(zset.reverseRangeWithScores(anyString(), anyLong(), anyLong())).thenThrow(down());
        when(zset.incrementScore(anyString(), anyString(), anyDouble())).thenThrow(down());
        when(template.delete(any(java.util.Collection.class))).thenThrow(down());
        org.mockito.Mockito.doThrow(down()).when(template).convertAndSend(anyString(), any());

        assertTrue(redis.get("k").isEmpty());
        assertFalse(redis.set("k", "v", Duration.ofMinutes(1)));
        assertEquals(List.of(), redis.zRevRangeWithScores("top", 10));
        assertFalse(redis.zIncrBy("top", "p1", 1, Duration.ofDays(3)));
        assertFalse(redis.delete("k"));
        assertFalse(redis.publish("ch", "msg"));
    }

    @Test
    void readsValuesWhenRedisIsUp() {
        when(values.get("k")).thenReturn("v");
        assertEquals("v", redis.get("k").orElseThrow());
        assertTrue(redis.set("k", "v", Duration.ofMinutes(1)));
    }

    @Test
    void mapsSortedSetEntries() {
        Set<TypedTuple<String>> tuples = new LinkedHashSet<>();
        tuples.add(TypedTuple.of("p1", 5.0));
        tuples.add(TypedTuple.of("p2", 2.0));
        when(zset.reverseRangeWithScores("top", 0, 9)).thenReturn(tuples);
        assertEquals(List.of(new RedisSafe.ZEntry("p1", 5.0), new RedisSafe.ZEntry("p2", 2.0)),
                redis.zRevRangeWithScores("top", 10));
    }

    @Test
    void setsTtlOnlyForNewSortedSet() {
        when(template.getExpire("top")).thenReturn(-1L);
        assertTrue(redis.zIncrBy("top", "p1", 1, Duration.ofDays(3)));
        verify(template).expire("top", Duration.ofDays(3));

        when(template.getExpire("top2")).thenReturn(100L);
        redis.zIncrBy("top2", "p1", 1, Duration.ofDays(3));
        verify(template, never()).expire("top2", Duration.ofDays(3));
    }
}
