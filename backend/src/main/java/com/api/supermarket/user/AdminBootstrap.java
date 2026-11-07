package com.api.supermarket.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.api.supermarket.user.dto.CreateUserRequest;

/**
 * Tạo tài khoản ADMIN đầu tiên khi collection {@code users} còn trống, mật khẩu lấy từ
 * {@code APP_ADMIN_PASSWORD}. Không có mật khẩu mặc định trong code.
 */
@Component
@Order(10)
@ConditionalOnExpression("!'${app.bootstrap.admin-password:}'.isBlank()")
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository repository;
    private final UserService users;
    private final String password;

    public AdminBootstrap(UserRepository repository, UserService users,
            @Value("${app.bootstrap.admin-password}") String password) {
        this.repository = repository;
        this.users = users;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (repository.count() > 0) {
                return;
            }
            users.create(new CreateUserRequest("admin", password, "Quản trị viên", null, null, Role.ADMIN, true));
            log.info("Đã tạo tài khoản ADMIN đầu tiên: admin");
        } catch (RuntimeException ex) {
            log.warn("Không tạo được tài khoản ADMIN đầu tiên: {}", ex.toString());
        }
    }
}
