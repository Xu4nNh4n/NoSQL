# Hệ thống quản lý siêu thị – Đồ án NoSQL

Ứng dụng quản lý một cửa hàng bán lẻ: nhập hàng theo lô, bán tại quầy có khuyến mãi, trả hàng, chốt ca thu ngân và thu hồi lô lỗi. Dữ liệu nằm trên 4 CSDL NoSQL, mỗi CSDL gánh một nghiệp vụ riêng; tắt CSDL nào thì chức năng của nó biến mất nhưng việc bán vẫn chạy.

## Vai trò của 4 CSDL

| CSDL | Dịch vụ | Dữ liệu và nghiệp vụ |
|---|---|---|
| **MongoDB** | Atlas (replica set) | Nguồn sự thật: người dùng, khách, nhóm hàng, nhà cung cấp, sản phẩm (thuộc tính linh hoạt), lô hàng, phiếu nhập, đơn (nhúng dòng hàng, lô đã xuất, thanh toán), phiếu trả, khuyến mãi 4 loại, chốt ca, `sync_queue`. Transaction nhiều document + cập nhật có điều kiện chống bán quá tồn; `$jsonSchema`; thống kê bằng `$facet` |
| **Cassandra** | Astra DB | Sổ cái chỉ thêm: thẻ kho theo sản phẩm/tháng, thanh toán theo đơn và theo thu ngân/ngày (chốt ca), lịch sử đăng nhập TTL 90 ngày |
| **Neo4j** | AuraDB Free | Đồ thị khách–đơn–sản phẩm–lô–NCC: truy vết và thu hồi lô, gợi ý mua kèm; dựng lại được từ MongoDB |
| **Redis** | Redis Cloud / Upstash | Refresh token (xoay vòng, dùng một lần), cache sản phẩm/khuyến mãi/sắp hết hàng, đơn treo 30 phút (Lua), top bán chạy (sorted set) |

MongoDB ghi nghiệp vụ và bản ghi `sync_queue` trong cùng một transaction; sau commit sự kiện được ghi sang Cassandra và Neo4j bằng khóa xác định (ghi lặp an toàn), job 60 giây thử lại phần còn chờ.

## Tác nhân

| Vai trò | Màn hình |
|---|---|
| `ADMIN` | Toàn bộ, thêm: khuyến mãi, nhóm hàng, người dùng, thống kê, truy vết/thu hồi lô, trạng thái hệ thống |
| `WAREHOUSE` | Sản phẩm & thẻ kho, tồn kho theo lô, phiếu nhập, nhà cung cấp |
| `STAFF` | Bán tại quầy, đơn hàng & trả hàng, chốt ca, khách hàng, sản phẩm |

## Cấu trúc thư mục

```
docker-compose.yml  Neo4j, Redis (và Cassandra tùy chọn) cho máy local
scripts/        Bật replica set cho MongoDB cài máy
backend/        Spring Boot 4.1 (Java 21) – package theo nghiệp vụ: auth, user, catalog, customer,
                inventory, promotion, sales, shift, graph, stats, sync, health, seed
frontend/       React 19 + Vite + TypeScript + Tailwind
nosql-scripts/  Script tạo cấu trúc, dữ liệu mẫu, truy vấn cơ bản/nâng cao, sao lưu cho từng CSDL
docs/           SRS v2.0 (Word), spec thiết kế, kế hoạch triển khai 1–10
Bruno/          Bộ request kiểm thử API
secrets/        Secure bundle của Astra (không commit)
```

## Chạy dự án

Có hai cách đặt CSDL, trộn được với nhau qua `.env`:

| CSDL | Chạy máy | Cloud |
|---|---|---|
| MongoDB | Bản cài Windows (service `MongoDB`) bật replica set 1 node | Atlas |
| Cassandra | `docker compose --profile cassandra up -d` (heap 512 MB) | Astra DB (mặc định, không tốn RAM) |
| Neo4j | `docker compose up -d` (heap 512 MB) | AuraDB |
| Redis | `docker compose up -d` (128 MB) | Redis Cloud / Upstash |

1. Sao chép `.env.example` thành `.env`, điền `JWT_SECRET` (≥ 32 ký tự), `APP_ADMIN_PASSWORD`, chuỗi kết nối. Astra: đặt `secure-connect-*.zip` vào `secrets/`, keyspace tạo trên giao diện Astra.
2. MongoDB cài máy (một lần, PowerShell quyền Administrator) – thêm `replication.replSetName: rs0` vào `mongod.cfg` và khởi động lại service; backend tự `replSetInitiate` khi `MONGODB_INIT_REPLSET=true`:

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\mongo-local-replset.ps1
   ```

3. Bật Docker Desktop rồi chạy Neo4j + Redis (Neo4j Browser: http://localhost:7474):

   ```bash
   docker compose up -d
   ```

4. Muốn có dữ liệu demo: `APP_SEED_DEMO=true` (tài khoản `admin`, `thukho01`, `thungan01`, `thungan02`; mật khẩu `APP_SEED_PASSWORD`, để trống thì dùng `APP_ADMIN_PASSWORD`).
5. Backend:

   ```bash
   cd backend && ./mvnw spring-boot:run
   ```

6. Frontend (terminal khác), mở http://localhost:3000:

   ```bash
   cd frontend && npm install && npm run dev
   ```

Swagger UI: http://localhost:8080/swagger-ui/index.html. Lúc khởi động backend tự tạo index + `$jsonSchema` MongoDB, bảng Cassandra, constraint Neo4j (`SCHEMA_INIT=true`). Tắt CSDL docker: `docker compose stop` (dữ liệu giữ trong volume).

## Kiểm thử

```bash
cd backend && ./mvnw test
```

Chạy unit test và test phân quyền qua HTTP, không cần CSDL. Test trên cloud thật (bán đồng thời đơn vị cuối, transaction, text search, sổ cái, đồ thị) gắn tag `cloud`:

```bash
cd backend && ./mvnw test -Pcloud-tests
```

## Tài liệu

- [Mục lục tài liệu](docs/README.md) – SRS v2.0, spec thiết kế, 10 kế hoạch triển khai
- [Script NoSQL](nosql-scripts/README.md)
- Bản Drill/BigData trước đây còn trong lịch sử git ở commit `267e192`.
