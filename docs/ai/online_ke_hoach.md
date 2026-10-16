# Kế hoạch gỡ bán hàng online

> **For agentic workers:** Dùng `superpowers:executing-plans` triển khai lần lượt các task; giữ checklist và ghi lại kiểm tra sau từng task.

**Goal:** Vô hiệu hóa luồng mua online để ứng dụng chỉ nhận và quản lý bán hàng POS, trong khi giữ nguyên dữ liệu online lịch sử, kho/FEFO, thanh toán, trả hàng POS và pipeline Big Data.

**Architecture:** Tắt route và API online trước, sau đó gỡ code chỉ phục vụ shop/cart/nhận hàng. `SalesOrder` và các bảng giao dịch tiếp tục lưu dữ liệu cũ; không drop bảng/cột hoặc xóa record. Các truy vấn báo cáo vận hành mới chỉ tính giao dịch `IN_STORE`; event cũ được giữ và lọc bằng `order_id`/bảng nguồn có thật.

**Tech Stack:** Spring Boot 4 / Java 21, Liquibase, React/TypeScript, MySQL, Cassandra/Astra event export, Apache Drill, Parquet.

**Spec:** `docs/ai/online_khao_sat.md` và yêu cầu người dùng ngày 2026-10-16.

## Ràng buộc toàn cục

- Không sửa `.env`, token Astra, mật khẩu hoặc thông tin bí mật.
- Không xóa record, JSON/Parquet hiện có; không chạy `SQL/a.sql` (có `DROP TABLE`), không reset DB, không viết lại lịch sử Git.
- Không xóa changeset đã áp dụng hoặc drop bảng/cột. `order_type` và các trường giao hàng/xác nhận cũ ở lại để đọc lịch sử.
- POS tiếp tục tạo đơn `IN_STORE`, trừ tồn theo lô FEFO, ghi thanh toán/hoàn tiền và phát outbox.
- Không sửa nghiệp vụ trả hàng POS cho tới khi người dùng chốt chính sách. Chỉ gỡ nhánh giao hàng online, không thay logic `returnSalesOrder`.
- File dữ liệu lịch sử được giữ. Generator chỉ thay đổi dữ liệu sinh trong tương lai; Parquet mới phải dùng dataset/thư mục mới.
- Không báo PASS nếu lệnh chưa chạy; lỗi nền phải so với baseline và nêu nguyên nhân.

## Trọng điểm review

1. Checkout POS không được giữ tồn thay vì xuất tồn, và hủy POS không được cộng kho hai lần.
2. API đơn online không được còn gọi được sau khi tắt; đơn cũ không được phép kích hoạt giao hàng/hoàn tiền online mới.
3. CASH/CARD/chuyển khoản thủ công hợp lệ cho POS theo quy tắc đang có; COD chỉ được giữ nếu POS hiện tại dùng nó.
4. Báo cáo doanh thu và đối soát loại đơn `ONLINE` lịch sử; event Cassandra không thể phân loại channel chỉ bằng `event_type`.
5. Customer profile dùng tại quầy và quản lý khách không được nhầm với customer login/shop.

## Task 1: Khôi phục kiểm tra nền trước khi gỡ tính năng

**Files dự kiến:** `src/main/resources/db/changelog/changes/015-reconcile-product-inventory.*`, cấu hình test liên quan; các test đang lỗi theo báo cáo Surefire.

- [ ] Dùng root-cause từ baseline để làm changeset reconcile chạy trên MySQL và profile H2 bằng nhánh SQL tương thích theo DB, không đổi semantics.
- [ ] Sửa setup/mock của các test POS/service đang lỗi chỉ khi xác nhận test fixture không còn phản ánh interface hiện tại; không thay hành vi sản phẩm để ép test xanh.
- [ ] Chạy Maven tests và compile; lưu kết quả mới làm baseline đã hiệu chỉnh trước khi gỡ online.
- [ ] Lưu lại đúng nhóm test chạy, failure/error và lý do nếu vẫn còn lỗi; không gỡ code phụ thuộc test đó khi chưa hiểu.

## Task 2: Tắt entrypoint Shop và Cart API

**Files:** `frontend/src/App.tsx`, `Sidebar.tsx`, `Header.tsx`, `contexts/CartContext.tsx`, `pages/ShopPage.tsx`, `components/shop/*`, `controller/CartController.java`, `service/CartService.java`, `service/Impl/CartServiceImpl.java`, entity/repository/DTO Cart và test tương ứng, security/config.

- [ ] Bỏ đường dẫn Shop và menu/nút shop; `/` đi đến trang đăng nhập hoặc khu vực POS theo trạng thái/role, không redirect sang route bị xóa.
- [ ] Gỡ Cart provider và chỉ gỡ Cart API khi `rg` xác nhận không còn caller POS.
- [ ] Tắt đăng ký/login khách hàng online; giữ role và dữ liệu khách cũ, giữ `CustomerController` và trang khách dùng tại POS.
- [ ] Compile frontend/backend, kiểm tra route và endpoint công khai; chưa drop bảng `carts`/`cart_items`.

## Task 3: Thu gọn vòng đời đơn về POS

**Files:** `SalesOrderController`, `SalesOrderService`, `SalesOrderServiceImpl`, `SalesOrderRepository`, `SalesOrder`, `SalesOrderResponse`, request DTOs, `OrderReceiptService`, `OrderReceiptCompletionJob`, `UnpaidOrderExpiryJob`, `application.properties`, test SalesOrder.

- [ ] Loại endpoint tạo đơn online, customer order list, thanh toán chuyển khoản online, ship/deliver/delivery-failed và xác nhận nhận hàng online.
- [ ] Chỉ cho tạo đơn mới `IN_STORE`; giữ thao tác POS, hóa đơn, hủy đúng trạng thái, trả/hoàn hiện tại đến khi có thiết kế POS được chốt.
- [ ] Tắt job hết hạn/nhả reservation nếu sau khi trace xác nhận không còn luồng POS tạo reservation; giữ hàm hoàn kho phục vụ hủy POS.
- [ ] Đóng quyền truy cập đơn online lịch sử theo chính sách endpoint được xác minh; không sửa/xóa bản ghi lịch sử.
- [ ] Không sửa `returnSalesOrder`, bảng trả, trả theo số lượng, giá hoàn hoặc giao diện trả POS trong task này.

## Task 4: Báo cáo chỉ phản ánh POS và event lịch sử

**Files:** `OperationalAnalyticsService`, `DrillAnalyticsService`, `AstraAnalyticsService` nếu cần, `AnalyticsController`, frontend `OperationalAnalyticsPage`, `CashFlowReportsPage`, `DashboardPage`, `DrillReportExplanation`, `drill/analytics-queries.sql`, script Curate/Parquet.

- [ ] Bỏ báo cáo so sánh kênh nếu không còn ý nghĩa; đổi trang và diễn giải sang câu hỏi POS.
- [ ] Thêm điều kiện `order_type='IN_STORE'` vào báo cáo order/item/subtotal/đơn.
- [ ] Đối soát thanh toán/hoàn cần join `payment_records.order_id` với `sales_orders.order_id` để loại giao dịch online lịch sử; chọn tên database đúng theo dataset.
- [ ] Giữ stock movement không thuộc đơn và event lịch sử online. Giữ nguyên JSON/Parquet; không chạy CTAS ghi đè. Nếu tạo Parquet mới, xuất vào một thư mục/version mới và ghi manifest/hash.
- [ ] Chỉ kết luận báo cáo tương đương sau khi Docker/Drill chạy được và so sánh các chỉ tiêu POS trước/sau trên cùng query window.

## Task 5: Sinh dữ liệu POS-only cho lần chạy tiếp theo

**Files:** `database/simulation/generate.py`, `database/simulation/README.md`, `SQL/seed-current-schema.sql`, docs SQL/Big Data phụ thuộc kênh.

- [ ] Generator mới chỉ sinh giao dịch IN_STORE với hình thức thanh toán POS hiện được code hỗ trợ; giữ hủy/trả/kho/lô/events phù hợp.
- [ ] Seed tạo hồ sơ khách dùng tại quầy nếu cần, không tạo customer login shop.
- [ ] Không ghi đè dataset `sim-421014-100000-2026-10-14` hoặc event/Parquet trước đây.

## Task 6: Xác minh và báo cáo

- [ ] Chạy frontend build, backend compile/test; phân biệt baseline đã biết với regression mới.
- [ ] Khi Docker/Drill hoạt động, gọi API dashboard/cash flow/reconciliation/operational và chạy SQL Drill trước/sau cho cùng ngày; so sánh từng số POS, không ép quality thành PASS.
- [ ] Xác minh endpoint/route online đã bị tắt, POS vẫn tạo/đọc/hủy hóa đơn, dữ liệu lịch sử và event files còn nguyên.
- [ ] Ghi `docs/ai/go_online_ket_qua.md` và `docs/ai/bao_cao_tom_tat.md`, kèm bằng chứng, chỗ chưa chạy, rủi ro.

## Handoff và giới hạn hiện tại

Người dùng đã chọn thực thi bỏ online và tạo nhánh riêng. Baseline test hiện lỗi 4/83; task 1 phải xử lý nguồn gốc hoặc ghi rõ blocker trước khi gỡ code phụ thuộc. Docker daemon chưa hoạt động, vì vậy chưa thể chạy mốc Drill. Không xóa code phân tích/event có thể ảnh hưởng số liệu cho tới khi có truy vấn baseline và so sánh. Phần POS return chỉ được thiết kế sau khi hỏi từng câu; chưa triển khai ở nhánh này trước khi có lựa chọn.
