package com.api.supermarket.service.Impl;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.api.supermarket.dto.request.CreateUserRequest;
import com.api.supermarket.dto.request.UpdateUserRequest;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.dto.response.UserResponse;
import com.api.supermarket.entity.Role;
import com.api.supermarket.entity.User;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.repository.RoleRepository;
import com.api.supermarket.repository.UserRepository;
import com.api.supermarket.service.UserService;
import com.api.supermarket.validation.PaginationValidator;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "userId",
            "fullName",
            "userName",
            "email",
            "phone",
            "isActive",
            "createAt");

    private UserResponse mapToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setUserId(user.getUserId());
        response.setFullName(user.getFullName());
        response.setUserName(user.getUserName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());

        if (user.getRole() != null) {
            response.setRoleName(user.getRole().getRoleName());
        } else if (user.getRoleId() != null) {
            roleRepository.findById(user.getRoleId())
                    .ifPresent(r -> response.setRoleName(r.getRoleName()));
        }
        response.setIsActive(user.getIsActive());
        response.setCreateAt(user.getCreateAt());
        return response;
    }

    @Override
    public UserResponse getUserById(Long id) {
        return mapToResponse(userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng")));
    }

    @Override
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUserName(request.getUsername())) {
            throw new BadRequestException("Tên người dùng đã tồn tại");
        }

        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email đã tồn tại");
        }

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vai trò không tồn tại"));

        User newUser = new User();

        newUser.setFullName(request.getFullName());
        newUser.setUserName(request.getUsername());
        newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        newUser.setEmail(request.getEmail());
        newUser.setPhone(request.getPhone());
        newUser.setIsActive(request.getIsActive() == null || request.getIsActive());
        newUser.setRoleId(role.getRoleId());
        newUser.setRole(role);
        return mapToResponse(userRepository.save(newUser));
    }

    @Override
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        if (!existingUser.getUserName().equals(request.getUsername())
                && userRepository.existsByUserName(request.getUsername())) {
            throw new BadRequestException("Tên người dùng đã tồn tại");
        }

        if (request.getEmail() != null
                && !Objects.equals(existingUser.getEmail(), request.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email đã tồn tại");
        }

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vai trò không tồn tại"));

        existingUser.setFullName(request.getFullName());
        existingUser.setUserName(request.getUsername());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            existingUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        existingUser.setEmail(request.getEmail());
        existingUser.setPhone(request.getPhone());
        existingUser.setIsActive(request.getIsActive() != null ? request.getIsActive() : existingUser.getIsActive());
        existingUser.setRoleId(role.getRoleId());
        existingUser.setRole(role);

        return mapToResponse(userRepository.save(existingUser));
    }

    @Override
    public void deleteUser(Long id) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        userRepository.delete(existingUser);
    }

    @Override
    public PageResponse<UserResponse> filterUsers(
            int page,
            int size,
            String sortBy,
            String sortDir,
            String keyword,
            String roleName,
            String fullName,
            String email,
            String phone,
            Boolean active,
            boolean employeesOnly) {
        PaginationValidator.validate(page, size, sortBy, sortDir, ALLOWED_SORT_FIELDS);

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<User> userPage = userRepository.filterUsers(
                keyword,
                roleName,
                fullName,
                email,
                phone,
                active,
                employeesOnly,
                pageable);

        List<UserResponse> users = userPage.getContent()
                .stream()
                .map(this::mapToResponse)
                .toList();
        return new PageResponse<>(
                users,
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages(),
                userPage.isLast());
    }
}
