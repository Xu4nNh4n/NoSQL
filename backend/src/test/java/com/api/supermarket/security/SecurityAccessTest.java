package com.api.supermarket.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** NFR-01, NFR-02 qua HTTP thật: thiếu hoặc sai token → 401, sai vai trò → 403. Không cần CSDL. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityAccessTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwt;

    private String bearer(String role) {
        return "Bearer " + jwt.generateToken(new JwtPrincipal("u1", "tester", role));
    }

    @Test
    void missingTokenIs401() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenIs401() throws Exception {
        String token = bearer("ADMIN");
        mvc.perform(get("/api/roles").header("Authorization", token.substring(0, token.length() - 2) + "xx"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void removedManagerRoleGetsNoAccess() throws Exception {
        mvc.perform(get("/api/roles").header("Authorization", bearer("MANAGER"))).andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotManageUsers() throws Exception {
        mvc.perform(get("/api/users").header("Authorization", bearer("STAFF"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/health/datastores").header("Authorization", bearer("WAREHOUSE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogRolesAreEnforced() throws Exception {
        mvc.perform(get("/api/suppliers").header("Authorization", bearer("STAFF"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/customers").header("Authorization", bearer("WAREHOUSE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/products/active").header("Authorization", bearer("WAREHOUSE")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/p1").header("Authorization", bearer("STAFF"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/customers/k1").header("Authorization", bearer("STAFF"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/categories/c1").header("Authorization", bearer("WAREHOUSE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListRoles() throws Exception {
        mvc.perform(get("/api/roles").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("ADMIN"));
    }
}
