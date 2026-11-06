# Kế hoạch 1 – Dọn dẹp về khung tối thiểu cho bản NoSQL

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xóa toàn bộ Drill, Docker, MySQL/JPA và các trang phân tích; còn lại một backend Spring Boot và một frontend React biên dịch được, sẵn sàng gắn 4 CSDL NoSQL ở các kế hoạch sau.

**Architecture:** Không thêm tính năng. Backend chỉ giữ cấu hình bảo mật JWT, xử lý lỗi, chính sách thuần (FEFO, trả hàng, phân trang). JWT được tách khỏi entity JPA: token tự mang `userId`, `username`, `role`. Frontend giữ các trang nghiệp vụ, bỏ trang phân tích.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Maven wrapper, React 19 + Vite + TypeScript.

**Spec:** `docs/superpowers/specs/2026-11-07-nosql-redesign-design.md` (mục 15 – danh sách xóa, mục 19.6 – tài liệu nộp)

## Global Constraints

- Mốc an toàn trước khi xóa: commit `267e192` trên nhánh `nang-cap-drill-da-nguon`; làm việc trên nhánh `nosql-migration`.
- File `database/secure-connect-*.zip` là bí mật: chuyển sang `secrets/`, không được commit (`.gitignore` phải có `secrets/`).
- Không xóa: `docs/SRS_He_thong_quan_ly_sieu_thi_v1_1_viet_lai.docx`, `docs/SRS_He_thong_quan_ly_sieu_thi_v2_0.docx`, `docs/srs-assets/`, `docs/superpowers/specs/`, `docs/nosql/`, `Bruno/`, `img/`, `.agents/`, `.github/`.
- Vai trò hợp lệ sau dọn dẹp: `ADMIN`, `WAREHOUSE`, `STAFF` (bỏ `MANAGER` ở cấu hình bảo mật và frontend).
- Múi giờ ứng dụng `Asia/Ho_Chi_Minh`; tiền VND.

## Review Focus

- Token cũ có `role` là `MANAGER`: filter phải không cấp quyền gì (không lỗi 500).
- Token có `role` viết thường hoặc đã có tiền tố `ROLE_`: phải chuẩn hóa thành `ROLE_ADMIN`…
- Token hết hạn hoặc sai chữ ký: request đi tiếp không xác thực, API cần quyền trả 401 chứ không 500.
- `jwt.secret` ngắn hơn 32 byte: ứng dụng phải báo lỗi rõ lúc khởi động, không phải lúc đăng nhập.
- Frontend: đường dẫn cũ `/dashboard`, `/reports`, `/purchase-costs` đã bị xóa phải chuyển hướng về trang hợp lệ, không trắng trang.

---

## Lộ trình các kế hoạch tiếp theo

| Kế hoạch | Nội dung | Spec |
|---|---|---|
| 1 (file này) | Dọn dẹp về khung tối thiểu | mục 15 |
| 2 | Nền tảng: cấu hình 4 CSDL, `/api/health/datastores`, `counters`, `RedisSafe`, `sync_queue` + job | mục 3, 9, 19.5 |
| 3 | Tài khoản: người dùng, đăng nhập, refresh token Redis, lịch sử đăng nhập Cassandra | FR-01–03, FR-24 |
| 4 | Danh mục: nhóm hàng, nhà cung cấp, sản phẩm, khách hàng, cache | FR-04–07 |
| 5 | Kho: lô, FEFO, điều chỉnh, phiếu nhập, thẻ kho | FR-08–11 |
| 6 | Khuyến mãi: `PromotionEngine` (TDD) + API | FR-15 |
| 7 | Bán, trả, đơn treo, top bán, chốt ca | FR-12–14, 16, 17, 20 |
| 8 | Neo4j: ghi đồ thị, truy vết, gợi ý, dựng lại | FR-18, 19 |
| 9 | Hỗ trợ: thống kê, thông báo realtime, sao lưu, đa luồng | FR-21–23, mục 19.3 |
| 10 | Frontend các trang mới, `nosql-scripts/`, dữ liệu mẫu, README | mục 14, 19.4 |

Mỗi kế hoạch được viết chi tiết khi kế hoạch trước xong, để dùng đúng tên lớp và hàm đã có thật.

---

### Task 1: Xóa tệp và thư mục ngoài backend/frontend

**Files:**
- Delete: `drill/`, `analytics/`, `docker-compose.yml`, `.dockerignore`, `SQL/`, `database/` (sau khi chuyển bundle), `.tmp/`, `CONTEXT.md`, `HELP.md`
- Delete: `docs/benchmark/`, `docs/word/`, `docs/ai/`, `docs/antigravity/`, `docs/01_lo_trinh_va_kien_truc/`, `docs/KE_HOACH_NANG_CAP.md`, `docs/KE_HOACH_TRIEN_KHAI_BIGDATA.md`, `docs/NOSQL_BIGDATA_PROJECT_PLAN.txt`, `docs/promotions-contract.md`, `docs/superpowers/plans/2026-10-*.md`
- Create: `secrets/secure-connect-supermarket-bigdata.zip` (chuyển từ `database/`)
- Modify: `.gitignore` (thêm `secrets/`; bỏ các dòng analytics/drill), `docs/README.md` (viết lại mục lục tài liệu)

- [ ] **Step 1:** Chuyển bundle: `mkdir -p secrets && mv database/secure-connect-*.zip secrets/`
- [ ] **Step 2:** Xóa các đường dẫn trong danh sách trên bằng `git rm -r` (đã theo dõi) và `rm -rf` (`.tmp/`, file chưa theo dõi).
- [ ] **Step 3:** Sửa `.gitignore`: thêm `secrets/`; xóa các mục `analytics/*`, `analytics-supplier-price-archive/`, `*.parquet`, `*.ready.json`, `database/secure-connect-*.zip`.
- [ ] **Step 4:** Viết lại `docs/README.md` gồm: SRS v2.0, spec thiết kế, kế hoạch, `srs-assets/`.
- [ ] **Step 5:** Kiểm tra: `git status --short | grep secure-connect` không in gì; `ls secrets/` có file zip.
- [ ] **Step 6:** Commit `chore: remove Drill, Docker, MySQL artifacts and BigData docs`.

### Task 2: Backend về khung tối thiểu, JWT không phụ thuộc JPA

**Files:**
- Delete: `backend/src/main/java/com/api/supermarket/{controller,entity,repository,service,integration,dto/request}/`, các DTO response trừ `ApiErrorResponse`, `PageResponse`; `config/AstraCassandraConfig.java`; `exception/AnalyticsUnavailableException.java`, `exception/PriceChangedException.java`, `exception/InsufficientStockException.java`; `domain/PromotionSnapshot.java`
- Delete tests: `service/Impl/*`, `security/SecurityIntegrationTest.java`; `src/main/resources/db/`, `src/test/resources/application-test.properties` (H2)
- Modify: `backend/pom.xml` (bỏ `spring-boot-starter-data-jpa`, `mysql-connector-j`, `h2`, `spring-boot-starter-data-jpa-test`; giữ `java-driver-core`)
- Modify: `security/JwtService.java`, `security/JwtAuthenticationFilter.java`, `config/SecurityConfig.java`, `exception/GlobalExceptionHandler.java`
- Modify: `src/main/resources/application.properties`, `.env.example`
- Test: `backend/src/test/java/com/api/supermarket/security/JwtServiceTest.java`, `SupermarketApplicationTests.java`

**Interfaces:**
- Produces:
  - `record JwtPrincipal(String userId, String username, String role)` trong `security/JwtPrincipal.java`
  - `JwtService.generateToken(JwtPrincipal principal): String`
  - `JwtService.parse(String token): Optional<JwtPrincipal>` (rỗng khi sai chữ ký, hết hạn, sai định dạng)
  - `SecurityRoles.normalize(String role): Optional<String>` trong `security/SecurityRoles.java` – trả `ROLE_ADMIN|ROLE_WAREHOUSE|ROLE_STAFF`, rỗng với giá trị khác
  - Principal trong `SecurityContext` là `JwtPrincipal`

- [ ] **Step 1: Viết test thất bại `JwtServiceTest`**

```java
@Test void roundTripKeepsClaims() {
    var svc = new JwtService("0123456789abcdef0123456789abcdef", 900_000);
    var p = new JwtPrincipal("u1", "thungan01", "STAFF");
    assertEquals(Optional.of(p), svc.parse(svc.generateToken(p)));
}
@Test void expiredTokenIsEmpty() {
    var svc = new JwtService("0123456789abcdef0123456789abcdef", -1);
    assertTrue(svc.parse(svc.generateToken(new JwtPrincipal("u1", "a", "ADMIN"))).isEmpty());
}
@Test void tamperedTokenIsEmpty() { /* đổi 1 ký tự phần chữ ký → Optional.empty() */ }
@Test void shortSecretFailsFast() {
    assertThrows(IllegalStateException.class, () -> new JwtService("short", 1000));
}
@Test void normalizesRoles() {
    assertEquals(Optional.of("ROLE_ADMIN"), SecurityRoles.normalize("admin"));
    assertEquals(Optional.of("ROLE_STAFF"), SecurityRoles.normalize("ROLE_STAFF"));
    assertTrue(SecurityRoles.normalize("MANAGER").isEmpty());
    assertTrue(SecurityRoles.normalize(null).isEmpty());
}
```

- [ ] **Step 2:** Chạy `./mvnw -q test -Dtest=JwtServiceTest` → FAIL (chưa có lớp).
- [ ] **Step 3:** Xóa các gói/tệp trong danh sách Delete; sửa `pom.xml`.
- [ ] **Step 4:** Viết `JwtPrincipal`, `SecurityRoles`; sửa `JwtService` nhận `@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expirationMs` qua constructor, ném `IllegalStateException` khi secret < 32 byte; claim `userId`, `role`, subject = username.
- [ ] **Step 5:** Sửa `JwtAuthenticationFilter`: không dùng repository; `parse` token → `SecurityRoles.normalize(role)` → đặt `UsernamePasswordAuthenticationToken(principal, null, authorities)`; role không hợp lệ thì không đặt xác thực.
- [ ] **Step 6:** Sửa `SecurityConfig`: `anyRequest().hasAnyRole("ADMIN", "WAREHOUSE", "STAFF")`; `permitAll` cho `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`, `/v3/api-docs/**`, `/swagger-ui/**`, `/actuator/health`.
- [ ] **Step 7:** Sửa `GlobalExceptionHandler`: bỏ handler của các exception đã xóa.
- [ ] **Step 8:** Viết lại `application.properties`: bỏ `spring.datasource.*`, `spring.jpa.*`, `drill.*`, `astra.*`, `integration.*`, `analytics.*`; giữ server, multipart, logging, `jwt.secret=${JWT_SECRET}`, `jwt.expiration=${JWT_ACCESS_MS:900000}`, thêm `app.timezone=${APP_TIMEZONE:Asia/Ho_Chi_Minh}`. Viết lại `.env.example` theo bảng biến môi trường ở spec mục 3 (giá trị trống).
- [ ] **Step 9:** `SupermarketApplicationTests.contextLoads` chạy với `@TestPropertySource(properties = "jwt.secret=0123456789abcdef0123456789abcdef")`.
- [ ] **Step 10:** Chạy `./mvnw -q test` → PASS toàn bộ (FefoPolicyTest, ReturnPolicyTest, PaginationValidatorTest, JwtServiceTest, contextLoads).
- [ ] **Step 11:** Commit `refactor(backend): strip JPA/MySQL/Drill, keep JWT skeleton`.

### Task 3: Frontend bỏ trang phân tích và vai trò MANAGER

**Files:**
- Delete: `frontend/src/pages/{DashboardPage,ReportsPage,CashFlowReportsPage,OperationalAnalyticsPage,PurchaseCostsPage}.tsx`, `frontend/src/components/{analytics,chart,shop}/`
- Modify: `frontend/src/App.tsx` (bỏ route; `/admin`, `/dashboard`, `/reports`, `/purchase-costs`, `/supplier-prices` chuyển hướng về `/pos`), `frontend/src/components/layout/Sidebar.tsx` (bỏ mục menu), `frontend/src/types/index.ts` và mọi `allowedRoles` (bỏ `MANAGER`), `frontend/src/api/client.ts` (bỏ hàm gọi `/api/analytics`, `/api/integrations`)

- [ ] **Step 1:** Xóa tệp; sửa import và route.
- [ ] **Step 2:** `grep -rn "MANAGER\|analytics\|integrations" frontend/src` → không còn kết quả.
- [ ] **Step 3:** `cd frontend && npm run build` → thành công, không lỗi TypeScript.
- [ ] **Step 4:** Commit `refactor(frontend): drop analytics pages and MANAGER role`.

### Task 4: README mới

**Files:**
- Modify: `README.md`

- [ ] **Step 1:** Viết lại README: mục đích đồ án NoSQL, vai trò 4 CSDL (1 bảng), cấu trúc thư mục, cách chạy backend (`./mvnw spring-boot:run` với `.env`) và frontend (`npm run dev`), trạng thái "đang chuyển sang NoSQL – xem lộ trình trong kế hoạch".
- [ ] **Step 2:** Commit `docs: rewrite README for NoSQL version`.
