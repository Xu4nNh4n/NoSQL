package com.api.supermarket.service.Impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.api.supermarket.dto.request.CreateUserRequest;
import com.api.supermarket.dto.request.UpdateUserRequest;
import com.api.supermarket.dto.response.UserResponse;
import com.api.supermarket.entity.User;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.repository.RoleRepository;
import com.api.supermarket.repository.UserRepository;

class UserServiceImplTest {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private PasswordEncoder passwordEncoder;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userService = new UserServiceImpl(
            userRepository,
            roleRepository,
            passwordEncoder
        );

        when(roleRepository.existsById(3L)).thenReturn(true);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updateUserWithNormalEmailShouldSucceed() {
        User existingUser = createExistingUser(10L, "a@test.com");
        UpdateUserRequest request = createUpdateRequest("a.new@test.com");

        when(userRepository.findById(10L))
            .thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmail("a.new@test.com"))
            .thenReturn(false);

        UserResponse response = assertDoesNotThrow(
            () -> userService.updateUser(10L, request)
        );

        assertEquals("a.new@test.com", response.getEmail());
        verify(userRepository).save(existingUser);
    }

    @Test
    void updateUserWithExistingNullEmailShouldSucceed() {
        User existingUser = createExistingUser(10L, null);
        UpdateUserRequest request = createUpdateRequest("a@test.com");

        when(userRepository.findById(10L))
            .thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmail("a@test.com"))
            .thenReturn(false);

        UserResponse response = assertDoesNotThrow(
            () -> userService.updateUser(10L, request)
        );

        assertEquals("a@test.com", response.getEmail());
        verify(userRepository).save(existingUser);
    }

    @Test
    void updateUserWithUnchangedEmailShouldNotReportDuplicate() {
        User existingUser = createExistingUser(10L, "a@test.com");
        UpdateUserRequest request = createUpdateRequest("a@test.com");

        when(userRepository.findById(10L))
            .thenReturn(Optional.of(existingUser));

        UserResponse response = assertDoesNotThrow(
            () -> userService.updateUser(10L, request)
        );

        assertEquals("a@test.com", response.getEmail());
        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository).save(existingUser);
    }

    @Test
    void updateUserWithAnotherUsersEmailShouldThrowBadRequest() {
        User existingUser = createExistingUser(10L, "a@test.com");
        UpdateUserRequest request = createUpdateRequest("b@test.com");

        when(userRepository.findById(10L))
            .thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmail("b@test.com"))
            .thenReturn(true);

        BadRequestException exception = assertThrows(
            BadRequestException.class,
            () -> userService.updateUser(10L, request)
        );

        assertEquals("Email đã tồn tại", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUserWithoutIsActiveShouldDefaultToTrue() {
        CreateUserRequest request = createCreateRequest("a@test.com");
        request.setIsActive(null);

        when(userRepository.existsByUserName("test_user_a"))
            .thenReturn(false);
        when(userRepository.existsByEmail("a@test.com"))
            .thenReturn(false);

        UserResponse response = userService.createUser(request);

        assertEquals(true, response.getIsActive());
    }

    @Test
    void updateUserWithoutIsActiveShouldKeepExistingValue() {
        User existingUser = createExistingUser(10L, "a@test.com");
        existingUser.setIsActive(false);
        UpdateUserRequest request = createUpdateRequest("a@test.com");
        request.setIsActive(null);

        when(userRepository.findById(10L))
            .thenReturn(Optional.of(existingUser));

        UserResponse response = userService.updateUser(10L, request);

        assertEquals(false, response.getIsActive());
    }

    @Test
    void updateUserWithFalseShouldChangeIsActiveToFalse() {
        User existingUser = createExistingUser(10L, "a@test.com");
        existingUser.setIsActive(true);
        UpdateUserRequest request = createUpdateRequest("a@test.com");
        request.setIsActive(false);

        when(userRepository.findById(10L))
            .thenReturn(Optional.of(existingUser));

        UserResponse response = userService.updateUser(10L, request);

        assertEquals(false, response.getIsActive());
    }

    @Test
    void updateUserWithTrueShouldChangeIsActiveToTrue() {
        User existingUser = createExistingUser(10L, "a@test.com");
        existingUser.setIsActive(false);
        UpdateUserRequest request = createUpdateRequest("a@test.com");
        request.setIsActive(true);

        when(userRepository.findById(10L))
            .thenReturn(Optional.of(existingUser));

        UserResponse response = userService.updateUser(10L, request);

        assertEquals(true, response.getIsActive());
    }

    @Test
    void updateUserWithoutPasswordShouldKeepOldPassword() {
        User existingUser = createExistingUser(10L, "a@test.com");
        UpdateUserRequest request = createUpdateRequest("a@test.com");

        when(userRepository.findById(10L))
            .thenReturn(Optional.of(existingUser));

        userService.updateUser(10L, request);

        assertEquals("old-password", existingUser.getPasswordHash());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void updateUserWithNewPasswordShouldEncodePassword() {
        User existingUser = createExistingUser(10L, "a@test.com");
        UpdateUserRequest request = createUpdateRequest("a@test.com");
        request.setPassword("new-password");

        when(userRepository.findById(10L))
            .thenReturn(Optional.of(existingUser));
        when(passwordEncoder.encode("new-password"))
            .thenReturn("new-encoded-password");

        userService.updateUser(10L, request);

        assertEquals("new-encoded-password", existingUser.getPasswordHash());
        verify(passwordEncoder).encode("new-password");
    }

    private User createExistingUser(Long id, String email) {
        User user = new User();
        user.setUserId(id);
        user.setFullName("User A");
        user.setUserName("test_user_a");
        user.setPasswordHash("old-password");
        user.setEmail(email);
        user.setPhone("0900000010");
        user.setIsActive(true);
        user.setRoleId(3L);
        return user;
    }

    private CreateUserRequest createCreateRequest(String email) {
        CreateUserRequest request = new CreateUserRequest();
        request.setFullName("User A Updated");
        request.setUsername("test_user_a");
        request.setPassword("123456");
        request.setEmail(email);
        request.setPhone("0900000010");
        request.setIsActive(true);
        request.setRoleId(3L);
        return request;
    }

    private UpdateUserRequest createUpdateRequest(String email) {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setFullName("User A Updated");
        request.setUsername("test_user_a");
        request.setPassword(null);
        request.setEmail(email);
        request.setPhone("0900000010");
        request.setIsActive(true);
        request.setRoleId(3L);
        return request;
    }
}
