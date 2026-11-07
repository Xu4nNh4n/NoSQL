package com.api.supermarket.user;

import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.api.supermarket.auth.PasswordPolicy;
import com.api.supermarket.auth.RefreshTokenStore;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.user.dto.CreateUserRequest;
import com.api.supermarket.user.dto.UpdateUserRequest;
import com.api.supermarket.user.dto.UserResponse;
import com.api.supermarket.validation.PaginationValidator;

/** Quản lý người dùng (FR-02). */
@Service
public class UserService {
    public static final Set<String> SORT_FIELDS = Set.of("username", "fullName", "role", "createdAt");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final RefreshTokenStore tokens;
    private final AppTime time;

    public UserService(UserRepository users, PasswordEncoder encoder, RefreshTokenStore tokens, AppTime time) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.time = time;
    }

    public PageResponse<UserResponse> list(int page, int size, String sortBy, String sortDir, String keyword, Role role,
            Boolean active) {
        PaginationValidator.validate(page, size, sortBy, sortDir, SORT_FIELDS);
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Page<UserAccount> result = users.search(keyword, role, active, PageRequest.of(page, size, sort));
        return new PageResponse<>(result.map(UserResponse::from).getContent(), page, size, result.getTotalElements(),
                result.getTotalPages(), result.isLast());
    }

    public UserResponse get(String id) {
        return UserResponse.from(load(id));
    }

    public UserAccount load(String id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
    }

    public UserResponse create(CreateUserRequest req) {
        String username = UserFields.username(req.username());
        if (users.existsByUsername(username)) {
            throw new BadRequestException("Tên đăng nhập đã tồn tại");
        }
        String email = UserFields.email(req.email());
        String phone = UserFields.phone(req.phone());
        ensureContactUnique(email, phone, null);
        PasswordPolicy.check(req.password());

        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setFullName(UserFields.text(req.fullName()));
        u.setEmail(email);
        u.setPhone(phone);
        u.setRole(req.role());
        u.setActive(req.active() == null || req.active());
        u.setCreatedAt(time.now());
        u.setUpdatedAt(u.getCreatedAt());
        return UserResponse.from(users.save(u));
    }

    public UserResponse update(String id, UpdateUserRequest req, String actorId) {
        UserAccount u = load(id);
        String email = UserFields.email(req.email());
        String phone = UserFields.phone(req.phone());
        ensureContactUnique(email, phone, id);

        boolean roleChanged = u.getRole() != req.role();
        boolean deactivated = u.isActive() && !req.active();
        if (Objects.equals(id, actorId) && (roleChanged || deactivated)) {
            throw new BadRequestException("Không tự đổi vai trò hoặc khóa tài khoản của mình");
        }
        if ((roleChanged || deactivated) && isLastActiveAdmin(u)) {
            throw new BadRequestException("Không thể khóa hoặc hạ quyền ADMIN hoạt động cuối cùng");
        }
        boolean passwordReset = req.newPassword() != null && !req.newPassword().isBlank();
        if (passwordReset) {
            PasswordPolicy.check(req.newPassword());
            u.setPasswordHash(encoder.encode(req.newPassword()));
        }

        u.setFullName(UserFields.text(req.fullName()));
        u.setEmail(email);
        u.setPhone(phone);
        u.setRole(req.role());
        u.setActive(req.active());
        u.setUpdatedAt(time.now());
        UserAccount saved = users.save(u);
        if (roleChanged || deactivated || passwordReset) {
            tokens.revokeAll(id);
        }
        return UserResponse.from(saved);
    }

    /** Khóa (xóa mềm) và thu hồi mọi phiên. */
    public void deactivate(String id, String actorId) {
        UserAccount u = load(id);
        if (!u.isActive()) {
            return;
        }
        if (Objects.equals(id, actorId)) {
            throw new BadRequestException("Không thể tự khóa tài khoản của mình");
        }
        if (isLastActiveAdmin(u)) {
            throw new BadRequestException("Không thể khóa ADMIN hoạt động cuối cùng");
        }
        u.setActive(false);
        u.setUpdatedAt(time.now());
        users.save(u);
        tokens.revokeAll(id);
    }

    /** Email và SĐT duy nhất khi có giá trị. Index unique một phần vẫn chặn khi hai request ghi cùng lúc. */
    public void ensureContactUnique(String email, String phone, String excludeId) {
        if (email != null && users.existsByField("email", email, excludeId)) {
            throw new BadRequestException("Email đã được sử dụng");
        }
        if (phone != null && users.existsByField("phone", phone, excludeId)) {
            throw new BadRequestException("Số điện thoại đã được sử dụng");
        }
    }

    private boolean isLastActiveAdmin(UserAccount u) {
        return u.getRole() == Role.ADMIN && u.isActive() && users.countActiveAdmins() <= 1;
    }
}
