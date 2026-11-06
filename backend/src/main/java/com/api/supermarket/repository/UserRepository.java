package com.api.supermarket.repository;
import com.api.supermarket.entity.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
public interface UserRepository extends JpaRepository<User, Long>{
    
    boolean existsByUserName(String userName);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCaseAndUserIdNot(String email, Long userId);
    boolean existsByPhoneAndUserIdNot(String phone, Long userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.userName = :username")
    Optional<User> findByUserNameForUpdate(@Param("username") String username);
    
    Optional<User> findByUserName(String userName);
    
    @Query("""
            SELECT u FROM User u
            WHERE (:keyword IS NULL OR LOWER(u.userName) LIKE LOWER(CONCAT('%' , :keyword, '%')))
            AND (:roleName IS NULL OR LOWER(u.role.roleName) LIKE LOWER(CONCAT('%' , :roleName, '%')))
            AND (:fullName IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%' , :fullName, '%')))
            AND (:email IS NULL OR u.email = :email)
            AND (:phone IS NULL OR u.phone = :phone)
            AND (:active IS NULL OR u.isActive = :active)
            AND (:employeesOnly = false OR UPPER(u.role.roleName) IN ('ADMIN', 'MANAGER', 'STAFF', 'WAREHOUSE'))
            """)
    Page<User> filterUsers(
        @Param("keyword") String keyword,
        @Param("roleName") String roleName,
        @Param("fullName") String fullName,
        @Param("email") String email,
        @Param("phone") String phone,
        @Param("active") Boolean active,
        @Param("employeesOnly") boolean employeesOnly,
        Pageable pageable
    );
} 
