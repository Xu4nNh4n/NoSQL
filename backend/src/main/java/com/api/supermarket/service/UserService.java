package com.api.supermarket.service;

import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.dto.response.UserResponse;
import com.api.supermarket.dto.request.CreateUserRequest;
import com.api.supermarket.dto.request.UpdateUserRequest;
public interface UserService {

    
    public UserResponse getUserById(Long id); //

    public UserResponse createUser(CreateUserRequest request); //

    public UserResponse updateUser(Long id, UpdateUserRequest request); //

    public void deleteUser(Long id); //

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
        boolean employeesOnly
    );
}
