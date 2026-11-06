package com.api.supermarket.security;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.context.ActiveProfiles;

import com.api.supermarket.entity.Role;
import com.api.supermarket.entity.User;
import com.api.supermarket.repository.RoleRepository;
import com.api.supermarket.repository.UserRepository;

import jakarta.persistence.EntityManager;

import org.springframework.http.MediaType;
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
public class SecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc; //mockMVC dùng để mô phỏng các yêu cầu HTTP và kiểm tra các phản hồi từ các endpoint của ứng dụng mà không cần phải chạy server thực tế. Nó cho phép bạn kiểm tra các controller, filter, interceptor và các thành phần khác của ứng dụng web trong môi trường kiểm thử.

    @Autowired
    private UserRepository userRepository; // userRepository được sử dụng để truy cập và thao tác với dữ liệu người dùng trong quá trình kiểm thử bảo mật.

    @Autowired
    private RoleRepository roleRepository; // roleRepository được sử dụng để truy cập và thao tác với dữ liệu vai trò (role) trong quá trình kiểm thử bảo mật.

    @Autowired
    private PasswordEncoder passwordEncoder; // passwordEncoder được sử dụng để mã hóa mật khẩu người dùng trong quá trình kiểm thử bảo mật, đảm bảo rằng mật khẩu được lưu trữ và so sánh một cách an toàn.

    @Autowired
    private JwtService jwtService; // jwtService được sử dụng để tạo và xác thực các token JWT trong quá trình kiểm thử bảo mật, giúp kiểm tra các cơ chế xác thực và phân quyền dựa trên token.

    @Autowired
    private EntityManager entityManager;

    private Role adminRole;

    @BeforeEach
    void setUp(){
        adminRole = new Role();
        adminRole.setRoleName("ADMIN");
        adminRole = roleRepository.save(adminRole);

        createUser("admin_test", "123456", adminRole, true);
    }

    @Test
    void validLoginShouldReturnToken() throws Exception{
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "username": "admin_test",
                            "password": "123456"
                            }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void invalidLoginShouldReturn401() throws Exception{
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "username": "admin_test",
                            "password": "sai-mat-khau"
                            }
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void catalogWithoutTokenShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/products"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyCustomerCannotReadInternalCatalog() throws Exception {
        Role customerRole = new Role();
        customerRole.setRoleName("CUSTOMER");
        customerRole = roleRepository.save(customerRole);
        createUser("legacy_customer", "123456", customerRole, true);

        mockMvc.perform(get("/api/products")
                .header("Authorization", "Bearer " + generateTokenFor("legacy_customer")))
            .andExpect(status().isForbidden());
    }

    @Test
    void protectedApiWithoutTokenShouldReturn401() throws Exception{
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenShouldReturn401() throws Exception{
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer token-khong-hop-le"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void lockedAccountTokenShouldReturn401() throws Exception {
        // Arrange: Tạo token hợp lệ khi tài khoản admin vẫn đang hoạt động.
        // Làm theo thứ tự này để mô phỏng trường hợp thực tế:
        // người dùng đã đăng nhập và có token, nhưng sau đó bị quản trị viên khóa tài khoản.
        String token = generateTokenFor("admin_test");

        // Đọc lại admin, chuyển isActive thành false rồi ghi thay đổi xuống database H2.
        User admin = userRepository.findByUserName("admin_test").orElseThrow();
        admin.setIsActive(false);
        userRepository.saveAndFlush(admin);

        // Xóa cache của EntityManager để JwtAuthenticationFilter bắt buộc đọc trạng thái
        // isActive=false mới nhất từ database, thay vì dùng lại User cũ trong bộ nhớ.
        entityManager.clear();

        // Act: Gọi API được bảo vệ bằng token vốn có chữ ký và thời hạn hợp lệ.
        // Assert: Filter tìm thấy tài khoản đã bị khóa nên không tạo Authentication.
        // Spring Security xem request là chưa xác thực và trả HTTP 401 Unauthorized.
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminShouldAccessAdminApi() throws Exception {
        // Arrange: Tạo token của tài khoản có Role ADMIN.
        // JwtAuthenticationFilter sẽ chuyển Role này thành authority ROLE_ADMIN.
        String token = generateTokenFor("admin_test");

        // Act: Gửi token theo đúng định dạng "Bearer <token>" lên API /api/users.
        // Assert: @PreAuthorize("hasRole('ADMIN')") chấp nhận ROLE_ADMIN nên API trả 200.
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void insufficientRoleShouldReturn403() throws Exception {
        // Arrange: Tạo Role CASHIER và một tài khoản đang hoạt động thuộc Role đó.
        Role cashierRole = new Role();
        cashierRole.setRoleName("CASHIER");
        cashierRole = roleRepository.save(cashierRole);
        createUser("cashier_test", "123456", cashierRole, true);

        // Token này hợp lệ và xác thực được người dùng, nhưng chỉ chứa quyền ROLE_CASHIER.
        String token = generateTokenFor("cashier_test");

        // Act: CASHIER gọi API chỉ dành cho ADMIN.
        // Assert: Người dùng đã đăng nhập nhưng không đủ quyền nên phải nhận 403 Forbidden.
        // Khác với 401: 401 là chưa xác thực; 403 là đã xác thực nhưng không được phép truy cập.
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    private void createUser(String username, String password, Role role, boolean active) {
        // Hàm hỗ trợ tạo dữ liệu User dùng chung giữa các test, tránh lặp lại nhiều đoạn setup.
        User user = new User();
        user.setFullName("Security Test User");
        user.setUserName(username);

        // Trong database phải lưu BCrypt hash, không lưu mật khẩu thuần "123456".
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRoleId(role.getRoleId());
        user.setIsActive(active);
        userRepository.saveAndFlush(user);

        // Buộc lần truy vấn sau đọc lại User và quan hệ Role từ database test.
        entityManager.clear();
    }

    private String generateTokenFor(String username) {
        // Đọc User cùng quan hệ Role từ database rồi đưa vào JwtService.
        // JwtService dùng username, userId và roleName để tạo các claim trong token.
        User user = userRepository.findByUserName(username).orElseThrow();
        return jwtService.generateToken(user);
    }
}
