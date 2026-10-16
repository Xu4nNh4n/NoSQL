# Khảo sát phạm vi gỡ bán hàng online

Ngày khảo sát: 2026-10-16. Đây là kiểm kê chỉ đọc trước khi sửa; lịch sử Git và dữ liệu hiện có được giữ nguyên.

## Kết luận ngắn

Online không nằm trong một module độc lập. Giao diện mua online và giỏ hàng là riêng, nhưng đơn online đi qua `SalesOrderServiceImpl`, dùng chung `sales_orders`, dòng đơn, sản phẩm, kho lô FEFO, thanh toán, trả hàng và outbox với POS. Vì vậy chỉ ẩn trang shop là chưa đủ; mọi nhánh online trong dịch vụ đơn hàng và truy vấn phân tích cần được xử lý có chủ đích. Các bảng/cột đã áp dụng không được drop hoặc xóa dữ liệu lịch sử. `order_type` còn giá trị để nhận diện đơn cũ và lọc báo cáo.

## Các vùng bị ảnh hưởng

| Vùng | File/điểm đã xác nhận | Ghi chú và rủi ro |
|---|---|---|
| Điều hướng React | `frontend/src/App.tsx`, `components/layout/Sidebar.tsx`, `components/layout/Header.tsx` | `/` và `/shop` đang mở `ShopPage`; menu và nút nhanh còn dẫn đến Shop. Cần thay mặc định bằng luồng đăng nhập/nội bộ để khách không rơi vào route không tồn tại. |
| Shop và giỏ | `pages/ShopPage.tsx`, `contexts/CartContext.tsx`, `components/shop/*`, `components/shop/CustomerReceiptPanel.tsx`, `components/orders/OrderReceiptSummary.tsx` | Checkout gọi `POST /carts`, `/items`, `/checkout`; lịch sử đơn gọi `/orders/mine`; có lựa chọn COD/chuyển khoản và xác nhận nhận hàng. Đây là phần UI online riêng. |
| POS | `pages/PosPage.tsx` | POS tạo đơn qua API đơn hàng trực tiếp, không dùng Cart API. Giữ đường này và không xóa tra cứu khách hàng, hóa đơn in, hay thanh toán POS. |
| API giỏ | `CartController`, `CartService`, `Impl/CartServiceImpl`, `Cart`, `CartItem`, `CartRepository`, `CartItemRepository`, DTO `CreateCartRequest`, `CartItemRequest`, `CheckoutCartRequest`, `CartResponse`, `CartItemResponse` | Chỉ Shop gọi các endpoint `/api/carts/**`; hiện cùng service chấp nhận cả `IN_STORE` lẫn `ONLINE`, nhưng POS UI không gọi. Có thể gỡ API và code giỏ khỏi runtime, giữ migration/bảng lịch sử. |
| API và service đơn hàng | `SalesOrderController`, `SalesOrderService`, `Impl/SalesOrderServiceImpl`, `SalesOrderRepository`, `SalesOrder`, `SalesOrderResponse`, `SalesOrderRequest`, `OrderItemRequest`, `PaymentConfirmationRequest` | `/orders/mine`, `/receipt`, `/pay`, `/ship`, `/deliver`, `/delivery-failed` là hoặc chủ yếu dành cho online. `POST /orders`, đọc đơn, hủy, trả và hoàn tiền là luồng dùng chung; phải giữ POS và xử lý riêng nhánh tạo đơn online, giữ/nhả tồn, vận chuyển và quyền sở hữu tài khoản. |
| Xác nhận nhận hàng | `OrderReceiptService`, `OrderReceiptCompletionJob`, `OrderReceiptRequest`, `SalesOrderRepository`, `SalesOrder` | Chỉ cho `orderType=ONLINE`; sinh trạng thái chờ xác nhận/quá hạn và sự kiện. Không ảnh hưởng thao tác trả kho trực tiếp, nhưng cần gỡ khỏi ứng dụng nếu bỏ online. |
| Hết hạn thanh toán | `UnpaidOrderExpiryJob`, `SalesOrderRepository.findExpiredPaymentOrderIds`, `SalesOrderServiceImpl.expireUnpaidOrder`, `application.properties` | Giải phóng reservation của đơn chờ chuyển khoản. Cần xác nhận reservation chỉ được tạo cho online trước khi bỏ job; không xóa logic trả kho còn dùng trong hủy đơn. |
| Hồ sơ khách và tài khoản | `CustomerController`, `CustomerAccountController`, `CustomerAccountService`, `CustomerAccountServiceImpl`, `CustomerService*`, `Customer`, `CustomerRepository`, `AuthController` | Hồ sơ khách được POS, màn quản lý khách và báo cáo sử dụng. Chỉ luồng đăng ký/đăng nhập tài khoản khách, hồ sơ “của tôi” là ứng viên online; không xóa bảng khách, lịch sử mua, hay chức năng thu ngân tra cứu khách. |
| Thanh toán và kho | `SalesOrderServiceImpl`, `InventoryBatchService`, `PaymentRecord`, `BatchAllocation`, `StockMovement`, `DataSyncPublisher` | Đây là phần dùng chung. POS bán tiền mặt/thanh toán tại quầy và xuất kho lô; online giữ trước, chuyển giữ thành xuất khi giao, giải phóng nếu hủy/hết hạn. Xóa nhầm nhánh này có thể làm lệch tồn và dòng tiền POS. |
| CSDL lịch sử | Liquibase `008-business-foundation.yaml`, `009-inventory-and-customer-account.yaml`, `010-payments-and-inventory-history.yaml`, `012-partial-returns.sql`, `016-order-receipt-confirmation.sql`; `db.changelog-master.yaml`; `SQL/a.sql`; `SQL/seed-current-schema.sql` | Schema có `order_type`, `delivery_status`, `payment_expires_at`, địa chỉ/người nhận và cột xác nhận nhận hàng; `carts`, `cart_items` được tạo trong lịch sử migration. Không sửa changeset đã áp dụng, không chạy `SQL/a.sql` (file có DROP TABLE), không drop cột/bảng. Seed có tài khoản CUSTOMER mẫu; khách và hồ sơ khách vẫn có thể dùng cho POS. |
| Báo cáo backend | `AnalyticsController`, `OperationalAnalyticsService`, `DrillAnalyticsService`, `AstraAnalyticsService`, `frontend/src/pages/OperationalAnalyticsPage.tsx`, `CashFlowReportsPage.tsx`, `DashboardPage.tsx`, `DrillReportExplanation.tsx` | Báo cáo CHANNELS đang so sánh IN_STORE/ONLINE. Doanh thu loại đơn theo trạng thái hoàn tất; đối soát/dòng tiền đọc sự kiện Astra và/hoặc MySQL. Với event tiền chỉ có `order_id`, có thể phải JOIN `sales_orders.order_type` để loại giao dịch online cũ; không được lọc bằng suy đoán event name. |
| Drill/Parquet | `drill/analytics-queries.sql`, `drill/curate-events-parquet.ps1`, `drill/curate-all-events-parquet.ps1`, `OperationalAnalyticsService`, thư mục `analytics/**` | Schema sự kiện lưu `order_id`, `event_type`, `payment_method`, metadata có `orderType`; luồng xuất/curate hiện giữ sự kiện theo ngày. Không xóa JSON/Parquet lịch sử; truy vấn mới cần chịu được event online cũ và phân biệt event kho dùng chung với event của đơn. |
| Mô phỏng dữ liệu | `database/simulation/generate.py`, `database/simulation/README.md`, `database/cassandra/schema-and-seed.cql`, `database/cassandra/local-schema.cql`, `SQL/seed-current-schema.sql` | Generator hiện sinh cả IN_STORE/ONLINE, COD/chuyển khoản, hủy và trả; cần chỉnh generator cho dataset mới là POS-only nhưng không xóa các file dataset đã sinh. Cassandra/Astra lưu event phân tích, không phải nguồn quyền quyết định tồn/tiền. |
| Test | `src/test/**` | Có test Cart, SalesOrder, expiry, security, analytics và policy FEFO/return. Cần phân biệt test online bỏ khỏi suite với test chức năng POS giữ lại. Baseline hiện tại có lỗi trước khi sửa, ghi ở mục dưới. |

## Ranh giới nghiệp vụ nên giữ

- Giữ hóa đơn POS, sản phẩm, khách hàng, tồn lô/FEFO, thanh toán thực thu/hoàn tiền, lịch sử trả hàng, stock movement và đồng bộ outbox.
- Ngừng tạo luồng shop/checkout online, xác nhận nhận hàng, giao hàng và yêu cầu thanh toán online; không xóa đơn online cũ hay các event đã xuất.
- Giữ nguyên schema lịch sử đã triển khai. Có thể thêm changeset tương thích về sau nếu cần, nhưng bước này không cần xóa bảng hoặc cột để tắt chức năng.
- Báo cáo mới nên tính doanh thu POS từ đơn `IN_STORE`; dữ liệu event online cũ vẫn còn trong nguồn nhưng không được cộng vào KPI POS. Tồn nhập/điều chỉnh kho không thuộc một đơn online nên vẫn được giữ.

## Mốc trước khi gỡ

- Frontend: `npm.cmd run build` kết thúc mã thoát 0; Vite cảnh báo bundle JavaScript lớn hơn 500 kB.
- Backend compile: lần compile Maven tiếp theo kết thúc `BUILD SUCCESS`; lần đầu trong cùng đợt báo lỗi đọc artifact `spring-boot-jdbc-4.1.1.jar`, chạy lại không thay đổi source thì compiler báo không còn gì cần biên dịch. Kết quả pass là compile incremental, chưa phải clean build.
- Backend test: Maven phải tải Surefire vì plugin chưa có trong cache offline. Sau khi tải và chạy đủ, kết quả `158 tests, 4 failures, 83 errors`. Nhóm lỗi tích hợp lặp lại do changeset `052-reconcile-product-inventory-from-batches` dùng `UPDATE ... JOIN` cú pháp MySQL nhưng profile test chạy H2. Một số unit test khác báo lỗi mock/setup (`DataSyncPublisher` null, role fixture không tồn tại); chưa sửa trong giai đoạn baseline.
- Drill: chưa chạy được. Drill cổng 8047/8048 và backend 8080 đều chưa lắng nghe; Docker Desktop có tiến trình giao diện và distro `docker-desktop` đã lên `Running`, nhưng Docker daemon vẫn báo không có pipe `dockerDesktopLinuxEngine`. Dữ liệu **đang có** trong `analytics/`: 1.100 JSON (~188 MB), 307 Parquet (~18.4 MB), cùng bộ mô phỏng `sim-421014-100000-2026-10-14` có manifest ghi 107.229 event, 22.222 đơn, 64.007 dòng hàng, 200 SKU và 782 phiếu trả. Có một Parquet manifest cho ngày 2026-10-08 và một bộ Parquet mô phỏng; các file này cần được giữ nguyên. MySQL cổng 3306 và MongoDB cổng 27017 có mở, nhưng không thể chạy federated SQL khi Drill chưa hoạt động.
- Output lệnh nằm tại `docs/ai/moc_truoc_khi_go_online/`. Không có API báo cáo nào được gọi thành công trong mốc này; không ghi số liệu giả.

## Rủi ro cần kiểm soát khi thực thi

1. Bỏ cả nhánh tồn reservation trong một lần có thể làm sai POS hoặc đơn lịch sử; cần chứng minh reservation chỉ phát sinh trong checkout online và giữ nguyên hàm giải phóng phù hợp với hủy POS.
2. Xóa trường/role khách hàng có thể phá màn quản lý khách, POS hoặc lịch sử hóa đơn. Chỉ vô hiệu hóa endpoint tài khoản khách sau khi dò mọi caller.
3. Bỏ `order_type`/delivery columns sẽ phá khả năng đọc dữ liệu lịch sử và câu Drill cũ; giữ schema để tương thích.
4. Event thanh toán và Parquet lịch sử không tự nói rõ channel ở cấp cột chuẩn; lọc bằng JOIN khóa đơn trong MySQL hoặc metadata đã xác nhận, rồi đối chiếu trên cùng khoảng thời gian.
5. Baseline hiện không xanh và Drill chưa truy vấn được, nên không thể tuyên bố tương đương số liệu trước/sau cho đến khi môi trường nguồn hoạt động và lỗi test nền được xử lý hoặc được ghi rõ là blocker.
