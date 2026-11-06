package com.api.supermarket.repository;
import com.api.supermarket.entity.*;
import org.springframework.data.jpa.repository.*;
public interface RoleRepository extends JpaRepository<Role, Long> {
    java.util.Optional<Role> findByRoleNameIgnoreCase(String roleName);
}
