package com.api.supermarket.service.Impl;
import com.api.supermarket.dto.response.RoleResponse;
import com.api.supermarket.entity.*;
import com.api.supermarket.repository.*;
import com.api.supermarket.service.*;
import org.springframework.stereotype.*;
import java.util.*;
@Service
public class RoleServiceImpl implements RoleService {
    private final RoleRepository roleRepository;
    public RoleServiceImpl(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    private RoleResponse mapToResponse(Role role){
        return new RoleResponse(
            role.getRoleId(),
            role.getRoleName(),
            role.getDescription(),
            role.getCreateAt()
        );
    }

    @Override
    public List<RoleResponse> getALlRoles() {
        return roleRepository.findAll().stream().map(this::mapToResponse).toList();
    }

    
}
