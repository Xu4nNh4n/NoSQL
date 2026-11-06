package com.api.supermarket.service.Impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.api.supermarket.dto.response.RoleResponse;
import com.api.supermarket.entity.Role;
import com.api.supermarket.repository.RoleRepository;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private RoleServiceImpl roleService;

    @Test
    void getAllRolesReturnsMappedRoles() {
        Role admin = new Role();
        admin.setRoleId(1L);
        admin.setRoleName("ADMIN");
        admin.setDescription("Administrator");
        Role staff = new Role();
        staff.setRoleId(2L);
        staff.setRoleName("STAFF");
        staff.setDescription("Sales staff");
        when(roleRepository.findAll()).thenReturn(List.of(admin, staff));

        List<RoleResponse> result = roleService.getALlRoles();

        assertEquals(2, result.size());
        assertEquals("ADMIN", result.get(0).getRoleName());
        assertEquals("STAFF", result.get(1).getRoleName());
    }
}
