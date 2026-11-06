# Mốc trước khi gỡ online

Ngày: 2026-10-16 (Asia/Bangkok). Nhánh: `go-online-tra-hang`.

| Hạng mục | Lệnh/nguồn | Kết quả đã quan sát |
|---|---|---|
| Frontend build | `npm.cmd run build` trong `frontend/` | Exit 0; TypeScript và Vite build thành công; Vite cảnh báo bundle JS 515.32 kB vượt ngưỡng 500 kB. Log: `frontend-build.txt`. |
| Backend compile | Maven `-o -Dmaven.test.skip=true compile` | Lần chạy lưu trong `maven-compile.txt`: `BUILD SUCCESS`, nhưng compiler incremental báo không còn file cần biên dịch. Trước đó có một lần compile lỗi đọc JAR cache; chạy lại không sửa source thì pass. |
| Backend test | Maven `test` sau khi cho phép tải Surefire từ Maven Central | 158 test: 4 failures, 83 errors, 0 skipped. Chi tiết: `maven-test-online.txt`; lượt offline ban đầu thiếu Surefire: `maven-test.txt`. |
| Drill/API | Kiểm tra TCP localhost 8047, 8048, 8080; `docker info`; trạng thái WSL | Các cổng không lắng nghe. Docker Desktop distro đã Running nhưng daemon không có pipe `dockerDesktopLinuxEngine`. Không chạy SQL/API report được; xem `drill-baseline.txt`. |
| Dữ liệu phân tích sẵn có | manifest và liệt kê file trong `analytics/` | Có event JSON, Parquet ngày 2026-10-08 và dataset mô phỏng 107.229 event/22.222 đơn/64.007 dòng/200 SKU/782 lượt trả. Không file nào bị ghi lại. |


## Nguyên nhân baseline test đã xác định

- Nhóm lỗi Spring context dừng ở Liquibase changeset `052-reconcile-product-inventory-from-batches`: cú pháp `UPDATE products ... JOIN (...) ... SET` dùng riêng MySQL nhưng integration test bật profile H2. Vì context không khởi tạo, Security, CreatedAt, app-context và các test service dựa Spring context lỗi dây chuyền.
- Các unit test còn có lỗi độc lập đã nhìn thấy: `SalesOrderServiceImplTest` gọi `DataSyncPublisher` chưa được mock; `UserServiceImplTest` truyền role không có trong mock repository. Chưa sửa các lỗi baseline.
- Không thể kết luận số liệu báo cáo trước/sau vì Drill/API chưa chạy. Không coi trạng thái này là PASS và không thay thế bằng số tự tính.

## Xác minh sau khi tắt các luồng online

- Frontend build sau khi bỏ Shop/receipt UI và chỉ giữ giỏ state cục bộ cho POS: exit 0.
- Maven main compile sau khi tắt API Cart/đăng ký, giới hạn đơn mới ở POS và lọc báo cáo: `BUILD SUCCESS` (154 source files).
- Maven test sau thay đổi hiện dừng trước khi chạy test: test compiler ở `CategoryServiceImplTest`, `CustomerServiceImplTest`, `ProductServiceImplTest`, `SalesOrderServiceImplTest` gọi method production đã bỏ/đổi. Log: `maven-test-pos-only.txt`.
- Lượt chạy trước khi Maven buộc biên dịch lại test source có 158 test, 4 failures, 73 errors; đây không phải kết quả kiểm chứng cuối vì dùng test class cũ và test lỗi mock/fixture. Xem `maven-test-after-h2.txt`.
- Chưa gọi API runtime hoặc chạy Drill SQL vì Docker daemon/Drill chưa hoạt động. Route mapping đã được kiểm tra trong source và compile; API security/runtime, SQL Drill, cash-flow/reconciliation và so sánh số trước/sau vẫn chưa chạy.
- Không thay dữ liệu MySQL/Mongo/Cassandra, không xóa JSON/Parquet, không chạy seed/generator/SQL load; chỉ sửa script/seed để lần chạy mới POS-only.
