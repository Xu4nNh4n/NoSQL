# Hệ thống quản lý siêu thị – Đồ án NoSQL

Ứng dụng quản lý một cửa hàng bán lẻ: nhập hàng theo lô, bán tại quầy có khuyến mãi, trả hàng, chốt ca thu ngân và thu hồi lô lỗi. Đồ án dùng 4 CSDL NoSQL, mỗi CSDL gánh một nghiệp vụ riêng.

> **Trạng thái:** đang chuyển từ bản Drill/MySQL sang NoSQL trên nhánh `nosql-migration`. Kế hoạch 1 (dọn dẹp) đã xong; backend hiện chỉ còn khung JWT. Xem lộ trình trong [kế hoạch 1](docs/superpowers/plans/2026-11-07-01-don-dep-nosql.md).

## Vai trò của 4 CSDL

| CSDL | Dịch vụ | Dữ liệu và nghiệp vụ |
|---|---|---|
| MongoDB | Atlas | Nguồn sự thật: người dùng, khách hàng, sản phẩm (thuộc tính linh hoạt), lô hàng, phiếu nhập, đơn hàng (nhúng dòng hàng, lô đã xuất, thanh toán), khuyến mãi 4 loại, chốt ca. Transaction và cập nhật có điều kiện chống bán quá tồn |
| Cassandra | Astra DB | Sổ cái chỉ thêm: thẻ kho theo sản phẩm và tháng, thanh toán theo đơn, thanh toán theo thu ngân và ngày (chốt ca), lịch sử đăng nhập 90 ngày (TTL) |
| Neo4j | AuraDB Free | Truy vết và thu hồi lô (lô → đơn → khách), gợi ý mua kèm |
| Redis | Redis Cloud / Upstash | Refresh token, cache sản phẩm và khuyến mãi, đơn treo (TTL 30 phút), top bán chạy hôm nay, thông báo realtime (Pub/Sub) |

MongoDB ghi trước trong một transaction kèm bản ghi `sync_queue`; Cassandra và Neo4j được ghi sau bằng khóa cố định nên ghi lại không tạo bản trùng. Redis chỉ giữ dữ liệu tạm; Redis lỗi thì hệ thống vẫn bán được.

## Tác nhân

| Vai trò | Việc chính |
|---|---|
| `ADMIN` | Người dùng, danh mục, khuyến mãi, thu hồi lô, xem chốt ca, thống kê, trạng thái hệ thống |
| `WAREHOUSE` | Phiếu nhập hàng, nhận lô, điều chỉnh tồn, thẻ kho, cảnh báo hết hạn |
| `STAFF` | Bán tại quầy, đơn treo, trả hàng, chốt ca, khách hàng |

## Cấu trúc thư mục

```
backend/    Spring Boot 4.1 (Java 21)
frontend/   React 19 + Vite + TypeScript
docs/       SRS v2.0, spec thiết kế, kế hoạch triển khai
Bruno/      Bộ request kiểm thử API
secrets/    Secure bundle của Astra (không commit)
```

## Chạy dự án

1. Sao chép `.env.example` thành `.env` ở thư mục gốc rồi điền chuỗi kết nối 4 CSDL và `JWT_SECRET` (ít nhất 32 ký tự). Đặt file `secure-connect-*.zip` của Astra vào `secrets/`.
2. Backend:

   ```bash
   cd backend && ./mvnw spring-boot:run
   ```

3. Frontend (terminal khác):

   ```bash
   cd frontend && npm install && npm run dev
   ```

Backend chạy ở `http://localhost:8080`, Swagger UI ở `/swagger-ui/index.html`.

## Tài liệu

- [Mục lục tài liệu](docs/README.md)
- [Spec thiết kế NoSQL](docs/superpowers/specs/2026-11-07-nosql-redesign-design.md)
- Bản Drill/BigData trước đây còn trong lịch sử git ở commit `267e192`.
