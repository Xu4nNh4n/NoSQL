package com.api.supermarket.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.api.supermarket.auth.LoginHistoryStore.LoginEvent;
import com.api.supermarket.auth.dto.AuthDtos.TokenResponse;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.exception.UnauthorizedException;
import com.api.supermarket.support.CloudTest;
import com.api.supermarket.user.Role;
import com.api.supermarket.user.UserAccount;
import com.api.supermarket.user.UserService;
import com.api.supermarket.user.dto.CreateUserRequest;
import com.api.supermarket.user.dto.UserResponse;

/** Luồng FR-01 trên 3 CSDL thật: MongoDB (tài khoản), Redis (refresh token), Cassandra (lịch sử). */
@CloudTest
class AuthFlowCloudTest {
    @Autowired
    UserService users;
    @Autowired
    AuthService auth;
    @Autowired
    MongoTemplate mongo;
    @Autowired
    AppTime time;

    @Test
    void loginRefreshRotateLogoutAndHistory() throws Exception {
        String username = "cloudtest" + System.nanoTime() % 1_000_000;
        UserResponse created = users.create(new CreateUserRequest(username, "matkhau123", "Cloud Test", null, null,
                Role.STAFF, true));
        try {
            TokenResponse first = auth.login(username, "matkhau123", "127.0.0.1", "junit");
            assertNotNull(first.refreshToken(), "Redis phải cấp refresh token");

            TokenResponse second = auth.refresh(first.refreshToken());
            assertThrows(UnauthorizedException.class, () -> auth.refresh(first.refreshToken()),
                    "token cũ phải bị từ chối sau khi xoay vòng");

            auth.logout(second.refreshToken());
            assertThrows(UnauthorizedException.class, () -> auth.refresh(second.refreshToken()));

            List<LoginEvent> history = List.of();
            for (int i = 0; i < 20 && history.isEmpty(); i++) { // ghi lịch sử chạy nền
                Thread.sleep(250);
                history = auth.loginHistory(created.id(), YearMonth.from(time.today()));
            }
            assertFalse(history.isEmpty(), "Cassandra phải có dòng lịch sử đăng nhập");
            assertTrue(history.get(0).success());
        } finally {
            mongo.remove(Query.query(Criteria.where("_id").is(created.id())), UserAccount.class);
        }
    }
}
