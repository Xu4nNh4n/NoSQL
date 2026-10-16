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
