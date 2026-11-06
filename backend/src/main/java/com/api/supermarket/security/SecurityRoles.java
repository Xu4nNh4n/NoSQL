package com.api.supermarket.security;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Ba vai trò của hệ thống và cách chuẩn hóa sang authority của Spring Security. */
public final class SecurityRoles {
    public static final String ADMIN = "ADMIN";
    public static final String WAREHOUSE = "WAREHOUSE";
    public static final String STAFF = "STAFF";

    private static final Set<String> VALID = Set.of(ADMIN, WAREHOUSE, STAFF);

    private SecurityRoles() {
    }

    /**
     * Chuẩn hóa tên vai trò về dạng {@code ROLE_X}.
     *
     * @param role tên vai trò, có thể viết thường hoặc đã có tiền tố ROLE_
     * @return authority hợp lệ, hoặc rỗng nếu vai trò không thuộc 3 vai trò của hệ thống
     */
    public static Optional<String> normalize(String role) {
        if (role == null) {
            return Optional.empty();
        }
        String name = role.trim().toUpperCase(Locale.ROOT);
        if (name.startsWith("ROLE_")) {
            name = name.substring(5);
        }
        return VALID.contains(name) ? Optional.of("ROLE_" + name) : Optional.empty();
    }
}
