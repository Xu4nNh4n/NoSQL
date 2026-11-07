package com.api.supermarket.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.api.supermarket.common.RedisSafe;

class RefreshTokenStoreTest {
    private RedisSafe redis;
    private RefreshTokenStore store;

    @BeforeEach
    void setUp() {
        redis = mock(RedisSafe.class);
        store = new RefreshTokenStore(redis, 7);
    }

    @Test
    void issueStoresOnlyTheHashWithTtl() {
        when(redis.set(anyString(), eq("u1"), eq(Duration.ofDays(7)))).thenReturn(true);
        String raw = store.issue("u1").orElseThrow();

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(redis).set(key.capture(), eq("u1"), eq(Duration.ofDays(7)));
        assertEquals("auth:refresh:" + RefreshTokenStore.hash(raw), key.getValue());
        assertTrue(!key.getValue().contains(raw), "raw token must not be stored");
        verify(redis).sAdd("auth:user-tokens:u1", Duration.ofDays(7), RefreshTokenStore.hash(raw));
    }

    @Test
    void issueReturnsEmptyWhenRedisIsDown() {
        when(redis.set(anyString(), anyString(), any())).thenReturn(false);
        assertTrue(store.issue("u1").isEmpty());
        verify(redis, never()).sAdd(anyString(), any(), any());
    }

    @Test
    void consumeUsesGetDelSoTokenWorksOnce() {
        String hash = RefreshTokenStore.hash("abc");
        when(redis.getAndDelete("auth:refresh:" + hash)).thenReturn(Optional.of("u1"), Optional.empty());
        assertEquals(Optional.of("u1"), store.consume("abc"));
        assertTrue(store.consume("abc").isEmpty());
        verify(redis).sRem("auth:user-tokens:u1", hash);
    }

    @Test
    void blankTokenIsRejectedWithoutRedis() {
        assertTrue(store.consume(" ").isEmpty());
        assertTrue(store.consume(null).isEmpty());
        verify(redis, never()).getAndDelete(anyString());
    }

    @Test
    void revokeAllDeletesEveryTokenAndTheIndex() {
        when(redis.sMembers("auth:user-tokens:u1")).thenReturn(Set.of("h1"));
        store.revokeAll("u1");
        verify(redis).delete("auth:refresh:h1", "auth:user-tokens:u1");
    }

    @Test
    void hashIsHexSha256() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", RefreshTokenStore.hash("abc"));
    }
}
