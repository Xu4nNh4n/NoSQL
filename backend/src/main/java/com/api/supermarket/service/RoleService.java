package com.api.supermarket.service;
import com.api.supermarket.dto.response.RoleResponse;
import java.util.*;
public interface RoleService {
    //Lấy danh sách vai trò
    List<RoleResponse> getALlRoles();

}
