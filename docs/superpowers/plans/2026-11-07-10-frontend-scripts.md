# Kế hoạch 10 – Frontend, script NoSQL, dữ liệu mẫu, README

**Goal:** Giao diện cho 15 FR trên API mới; thư mục `nosql-scripts/` cho hồ sơ nộp; dữ liệu demo đi qua đúng service nghiệp vụ; README hướng dẫn chạy.

**Spec:** mục 14, 19.4, 19.6.

## Tasks

- [x] **Dữ liệu mẫu:** `DemoDataSeeder` (`APP_SEED_DEMO=true`, chỉ khi chưa có sản phẩm): 4 tài khoản, 5 nhóm hàng, 6 NCC, 15 sản phẩm có thuộc tính riêng, phiếu nhập → nhận lô, 5 khuyến mãi đủ 4 loại, 8 khách, ~80 đơn rải 7 ngày, 3 phiếu trả. `OrderService.create(req, actor, orderedAt)` cho đơn quá khứ.
- [x] **`$jsonSchema`:** `mongodb/validators.json` áp lúc khởi động (`MongoValidators`, `validationLevel: moderate`, `validationAction: error`); tồn kho và tồn lô không âm.
- [x] **`nosql-scripts/`:** MongoDB, Cassandra, Neo4j, Redis – 16 truy vấn cơ bản, 27 nâng cao, hướng dẫn sao lưu/khôi phục. `01-create-collections.js` sinh từ `validators.json`.
- [x] **Frontend:** `types` (ID chuỗi), `api/client` tự làm mới token qua `/auth/refresh`, `AuthContext`, `useLoad`, `Table/Tabs/Field`. Trang: Bán tại quầy (khuyến mãi tự áp, dòng quà, đơn treo, gợi ý mua kèm, top bán, chống gửi lặp bằng `requestKey`, xử lý 409 `PRICE_CHANGED`), Đơn hàng (chi tiết, sổ thanh toán Cassandra, trả hàng một bước), Chốt ca, Khách hàng, Sản phẩm (thuộc tính linh hoạt, thẻ kho theo tháng), Nhóm hàng, Nhà cung cấp, Phiếu nhập, Tồn kho (lô, điều chỉnh, sắp hết hàng, sắp hết hạn, hết hạn, truy vết/thu hồi), Khuyến mãi (form theo loại + gợi ý Neo4j), Người dùng (lịch sử đăng nhập), Thống kê (biểu đồ, CSV), Trạng thái hệ thống. `npm run build` PASS.
- [x] **README** mới.
- [ ] Kiểm tra trên trình duyệt với CSDL cloud thật (chờ `.env`).
