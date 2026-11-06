# Bộ Dữ Liệu Học Tập 1,000 Hóa Đơn POS & Big Data Analytics (Lịch Sử 30 Ngày)

Tài liệu hướng dẫn và đặc tả kỹ thuật cho bộ dữ liệu học tập quy mô **1,000 giao dịch POS tại quầy**, phân bổ trải rộng **30 ngày lịch sử (01/10/2026 – 30/10/2026)**, hỗ trợ kiểm tra các nghiệp vụ phân tích chuỗi cung ứng, tồn kho cạn kiệt (hết hàng), đối soát đa nguồn **MySQL – Cassandra – JSON** qua **Apache Drill**.

---

## 1. Nguồn Gốc và Tuyên Bố Dữ Liệu

- **Phân loại**: Dữ liệu học tập và kiểm thử tích hợp thực tế (*Learning Dataset*).
- **Hành vi khách hàng thực tế**:
  - **~85% hóa đơn** là **khách vãng lai không tên** (`customer_name = NULL, customer_phone = NULL`), phản ánh đúng thực tế vận hành siêu thị: khách vào chọn hàng, thu ngân quét mã vạch POS, thanh toán (tiền mặt / chuyển khoản), lấy hóa đơn rồi đi.
  - **~15% hóa đơn** có thông tin khách hàng thân thiết với họ tên tiếng Việt thực tế.
  - Tuyệt đối không chứa tiền tố/hậu tố nhân tạo như `MẪU`, `TEST`, `DEMO`, `SEED`, `SIM`.
- **Tính toàn vẹn nghiệp vụ**:
  - Toàn bộ 1,000 hóa đơn được tạo qua quy trình chuẩn API POS: `POST /api/orders/quote` $\rightarrow$ nhận `quoteToken` $\rightarrow$ `POST /api/orders` bằng tài khoản STAFF.
  - Trừ kho vật lý tự động theo chính sách **FEFO** (`FefoPolicy`), phân bổ chính xác theo từng lô (`inventory_batches`) và lưu `batch_allocations` trên từng dòng hóa đơn.
  - Lưu trữ `PromotionSnapshot` bất biến tại thời điểm bán.
  - Nghiệp vụ trả hàng và hoàn tiền tuân thủ `ReturnPolicy`.
  - Đồng bộ đa nguồn qua Outbox Pattern (`data_sync_outbox`) sang Cassandra (`business_events_by_day`) và JSON payment logs (`analytics/pos-payments/<ngày UTC>/<eventId>.json`).

---

## 2. Đặc Tả Tồn Kho & Các Sản Phẩm Bán Hết Hàng (Out-of-Stock)

Để phục vụ kiểm thử dashboard vận hành chuỗi cung ứng, hệ thống thiết kế các trạng thái sản phẩm rõ ràng:

| SKU | Tên sản phẩm | Danh mục | Tồn khả dụng | Tốc độ bán (30 ngày) | Trạng thái Drill | Hành vi quản trị chuỗi cung ứng |
|:---:|---|---|:---:|:---:|:---:|---|
| **8** | **Sữa chua có đường 100g** | Sữa & Hàng mát | **0** | 5.30 cái/ngày | **`REORDER`** (Hết hàng hoàn toàn) | Gợi ý đặt hàng khẩn cấp (75 cái) để bù đắp tiêu thụ nhanh |
| **20** | **Chảo chống dính 24cm** | Gia dụng | **0** | 0.43 cái/ngày | **`REORDER`** (Hết hàng hoàn toàn) | Gợi ý đặt hàng ngay với NCC Minh Phát (7 cái) |
| **7** | **Cà phê sữa hòa tan 16g*10** | Bách hóa khô | **8** | 4.73 cái/ngày | **`REORDER`** (Sắp hết hàng $\le 15$) | Cảnh báo tồn dưới mức dự trữ an toàn (gợi ý 59 cái) |
| **1-6, 9-19** | 17 sản phẩm thiết yếu khác | Đồ uống, Đồ khô, Hóa phẩm | **~350 – 590** | Đa dạng | **`SUFFICIENT`** (Đủ hàng) | Tồn kho an toàn, vận hành kinh doanh bình thường |

### 2.2. Kịch Bản Quản Trị Hạn Dùng & Rủi Ro Vốn (Expiry & Value-at-Risk)

Nhằm phục vụ viết báo cáo về thuật toán phân tích cận hạn và rủi ro vốn tồn kho (FEFO), hệ thống đã thiết lập các lô hàng cận hạn điển hình:
- **Lô `LOT-11-01` (Nước mắm cá cơm 500ml)**: Hạn dùng `2026-11-05` (cận hạn 6 ngày). Tồn kho: 123 chai $\rightarrow$ Giá trị rủi ro (*Value at Risk*): **3,936,000 VNĐ**.
- **Lô `LOT-13-01` (Đường cát trắng 1kg)**: Hạn dùng `2026-11-10` (cận hạn 11 ngày). Tồn kho: 135 túi $\rightarrow$ Giá trị rủi ro (*Value at Risk*): **2,565,000 VNĐ**.

Khi truy vấn báo cáo Hạn dùng:
- Với **`horizon = 7` ngày**: Cảnh báo 1 lô hàng (`LOT-11-01`).
- Với **`horizon = 14` ngày**: Cảnh báo cả 2 lô hàng (`LOT-11-01` và `LOT-13-01`) với tổng rủi ro vốn là **6,501,000 VNĐ**.

---

## 3. Quy Mô và Thông Số Toàn Bộ Hệ Thống

| Chỉ số | Quy mô | Chi tiết |
|---|---|---|
| **Khoảng thời gian lịch sử** | **30 ngày** | Từ **01/10/2026** đến **30/10/2026** (giờ mở cửa: 08:30 – 20:30 VN). |
| **Tổng hóa đơn POS** | **1,000 đơn** | Trung bình ~33.3 đơn/ngày, phân bổ mượt mà giữa các ngày. |
| **Khách vãng lai (ẩn danh)** | **~850 đơn (~85%)** | `customer_name = NULL, customer_phone = NULL`. |
| **Khách có tên** | **~150 đơn (~15%)** | Họ tên tiếng Việt chuẩn hóa. |
| **Nhà cung cấp** | **6 đơn vị** | Công ty Minh Phát, An Phú, Tân Bình, v.v. |
| **Danh mục sản phẩm** | **20 SKU** | 8 ngành hàng siêu thị chuẩn hóa. |
| **Lô nhập kho** | **47 lô hàng** | Lô ban đầu + lô bổ sung giữa kỳ + lô quy mô `SCALE1000`. |
| **Sự kiện Cassandra** | **> 4,000 sự kiện** | Bảng `supermarket_analytics.business_events_by_day`. |
| **Cassandra Stock Snapshots** | **580 bản ghi** | Snapshot tồn kho cuối ngày (`stock_daily_by_day`) cho 29 ngày đã kết thúc. |
| **JSON Payment Logs** | **> 1,000 file log** | Lưu tại `analytics/pos-payments/<ngày UTC>/<eventId>.json` kèm SHA256 fingerprint. |
| **Đối soát Drill 30 ngày** | **30/30 ngày PASS** | Chênh lệch tiền thu `0.0`, hoàn `0.0`, dòng hàng `0.0`. |

---

## 4. Hướng Dẫn Kiểm Tra Trên Dashboard & API

### 4.1. Kiểm tra Báo Cáo Gợi Ý Nhập Hàng (Replenishment Report)
- **API**: `GET /api/analytics/operational?report=REPLENISHMENT&from=2026-10-01&to=2026-10-30`
- **Kết quả kỳ vọng**:
  - **SKU 20** và **SKU 8** đứng đầu danh sách với `usable_quantity = 0`, `stock_status = 'REORDER'`, `suggested_quantity > 0`.
  - **SKU 7** xuất hiện với `usable_quantity = 8`, `stock_status = 'REORDER'`.
  - Các mặt hàng khác có `stock_status = 'SUFFICIENT'`.

### 4.2. Kiểm tra Báo Cáo Đối Soát Đa Nguồn 30 Ngày (Reconciliation Report)
- **API**: `GET /api/analytics/reconciliation?from=2026-10-01&to=2026-10-30`
- **Kết quả kỳ vọng**:
  - Toàn bộ **30 ngày** đều đạt `quality_status = 'PASS'`.
  - Không có độ lệch giữa MySQL, Cassandra và POS Payment logs.

### 4.3. Kiểm tra Doanh Thu & Dòng Tiền (Cash Flow & Dashboard)
- **Báo cáo Dòng tiền**: `GET /api/analytics/cash-flow?from=2026-10-01&to=2026-10-30`
- **Báo cáo Tổng quan**: `GET /api/analytics/dashboard?from=2026-10-01&to=2026-10-30`
- **Báo cáo Top Bán Chạy**: `GET /api/analytics/operational?report=SALES&from=2026-10-01&to=2026-10-30`
