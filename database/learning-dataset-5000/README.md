# Bộ Dữ Liệu Học Tập 5,000 Hóa Đơn POS Bách Hóa Xanh (Lịch Sử 60 Ngày)

Tài liệu hướng dẫn và đặc tả kỹ thuật cho bộ dữ liệu học tập quy mô lớn **5,000 giao dịch POS tại quầy**, mô phỏng chính xác mô hình bán lẻ chuỗi minimart tiện ích (**Bách Hóa Xanh / WinMart+ / Co.op Food**), phân bổ trải rộng **60 ngày lịch sử (01/09/2026 – 30/10/2026)**, đồng bộ nhất quán đa nguồn **MySQL – Cassandra – JSON** và đối soát tự động qua **Apache Drill**.

---

## 1. Nguồn Gốc và Tuyên Bố Dữ Liệu

- **Phân loại**: Dữ liệu học tập và kiểm thử tích hợp thực tế (*Learning Dataset*).
- **Mô phỏng chuỗi siêu thị tiện ích (Bách Hóa Xanh format)**:
  - **Mật độ mua sắm**: ~83.3 giao dịch/ngày (rải đều các khung giờ từ 07:30 đến 20:30 VN).
  - **Quy mô giỏ hàng**: Đơn mua sắm nhỏ, nhanh (1 – 3 sản phẩm/đơn, số lượng 1 – 2 cái/sản phẩm), phản ánh đúng hành vi khách ghé mua nhu yếu phẩm hàng ngày.
  - **85.8% hóa đơn là khách vãng lai không tên** (`customer_name = NULL, customer_phone = NULL`), thanh toán tiền mặt/chuyển khoản quét mã QR rồi lấy hóa đơn in nhiệt ra về.
  - **14.2% hóa đơn có tên khách hàng thành viên** với họ tên tiếng Việt chuẩn hóa.
  - Tuyệt đối không chứa tiền tố/hậu tố nhân tạo như `MẪU`, `TEST`, `DEMO`, `SEED`, `SIM`.
- **Tính toàn vẹn nghiệp vụ**:
  - Toàn bộ 5,000 hóa đơn được tạo qua quy trình chuẩn API POS: `POST /api/orders/quote` $\rightarrow$ nhận `quoteToken` $\rightarrow$ `POST /api/orders` bằng tài khoản STAFF.
  - Trừ kho vật lý tự động theo chính sách **FEFO** (`FefoPolicy`), phân bổ chính xác theo từng lô (`inventory_batches`) và lưu `batch_allocations` trên từng dòng hóa đơn.
  - Lưu trữ `PromotionSnapshot` bất biến tại thời điểm bán.
  - Nghiệp vụ trả hàng và hoàn tiền tuân thủ `ReturnPolicy` (~40 phiếu trả hàng).
  - Đồng bộ đa nguồn qua Outbox Pattern (`data_sync_outbox`) sang Cassandra (`business_events_by_day`) và JSON payment logs (`analytics/pos-payments/<ngày UTC>/<eventId>.json`).

---

## 2. Đặc Tả Tồn Kho, Cảnh Báo Hết Hàng & Rủi Ro Vốn

| SKU | Tên sản phẩm | Danh mục | Tồn khả dụng | Tốc độ bán (30 ngày) | Trạng thái Drill | Hành vi quản trị chuỗi cung ứng |
|:---:|---|---|:---:|:---:|:---:|---|
| **8** | **Sữa chua có đường 100g** | Sữa & Hàng mát | **0** | 1.87 cái/ngày | **`REORDER`** (Hết hàng hoàn toàn) | Gợi ý đặt hàng khẩn cấp (27 cái) bù đắp tiêu dùng hàng ngày |
| **7** | **Cà phê sữa hòa tan 16g*10** | Bách hóa khô | **8** | 2.07 cái/ngày | **`REORDER`** (Sắp hết hàng $\le 15$) | Cảnh báo tồn dưới mức dự trữ an toàn (gợi ý 21 cái) |
| **20** | **Chảo chống dính 24cm** | Gia dụng | **0** | 0.20 cái/ngày | **`REORDER`** (Hết hàng hoàn toàn) | Gợi ý đặt hàng ngay với NCC Minh Phát (3 cái) |
| **1-6, 9-19** | 17 sản phẩm thiết yếu khác | Đồ uống, Đồ khô, Hóa mỹ phẩm | **~800 – 1,100** | Đa dạng | **`SUFFICIENT`** (Đủ hàng) | Lô hàng bổ sung giữa kỳ đảm bảo an toàn kho |

### 2.2. Lô Hàng Cận Hạn Cho Báo Cáo Rủi Ro Vốn (Value-at-Risk)

- **Lô `LOT-11-01` (Nước mắm cá cơm 500ml)**: Hạn dùng `2026-11-05` (cận hạn 6 ngày). Tồn kho: 123 chai $\rightarrow$ Giá trị rủi ro: **3,936,000 VNĐ**.
- **Lô `LOT-13-01` (Đường cát trắng 1kg)**: Hạn dùng `2026-11-10` (cận hạn 11 ngày). Tồn kho: 135 túi $\rightarrow$ Giá trị rủi ro: **2,565,000 VNĐ**.

---

## 3. Quy Mô Toàn Bộ Hệ Thống

| Chỉ số | Quy mô | Chi tiết |
|---|---|---|
| **Khoảng thời gian lịch sử** | **60 ngày** | Từ **01/09/2026** đến **30/10/2026** (2 tháng kinh doanh trọn vẹn). |
| **Tổng hóa đơn POS** | **5,000 đơn** | Trung bình ~83.3 đơn/ngày, phân bổ theo nhịp sinh hoạt dân cư. |
| **Khách vãng lai (ẩn danh)** | **~4,290 đơn (~85.8%)** | `customer_name = NULL, customer_phone = NULL`. |
| **Khách có tên** | **~710 đơn (~14.2%)** | Khách thân thiết có họ tên tiếng Việt. |
| **Nhà cung cấp** | **6 đơn vị** | Đầy đủ thông tin pháp lý, mã số thuế và hợp đồng cung ứng. |
| **Danh mục sản phẩm** | **20 SKU** | 8 ngành hàng siêu thị chuẩn hóa. |
| **Phiếu trả hàng & hoàn tiền** | **~40 phiếu** | Phản ánh đầy đủ các lý do: đổi ý, nhầm quy cách, móp bao bì, chờ hoàn (`REFUND_PENDING`). |
| **Sự kiện Cassandra** | **20,393 sự kiện** | Bảng `supermarket_analytics.business_events_by_day`. |
| **Cassandra Stock Snapshots** | **1,180 bản ghi** | Snapshot tồn kho cuối ngày (`stock_daily_by_day`): Tháng 9 (600), Tháng 10 (580). |
| **JSON Payment Logs** | **5,027 file log** | Lưu tại `analytics/pos-payments/<ngày UTC>/<eventId>.json` kèm SHA256. |
| **Đối soát Drill 60 ngày** | **60/60 ngày PASS** | Tháng 9: 30/30 ngày PASS (`151b7c6e-bb49-f81b-6491-28e7f68c5f6f`); Tháng 10: 30/30 ngày PASS (`151b7c6e-c6c9-e1c6-5a01-c21e627c5c22`). |

---

## 4. Hướng Dẫn Kiểm Tra Trên Dashboard & API

### 4.1. Đối Soát Đa Nguồn Theo Từng Tháng (Reconciliation)
- **Tháng 9**: `GET /api/analytics/reconciliation?from=2026-09-01&to=2026-09-30` $\rightarrow$ **PASS 30/30 ngày**.
- **Tháng 10**: `GET /api/analytics/reconciliation?from=2026-10-01&to=2026-10-30` $\rightarrow$ **PASS 30/30 ngày**.

### 4.2. Báo Cáo Gợi Ý Nhập Hàng 60 Ngày (Replenishment Report)
- **API**: `GET /api/analytics/operational?report=REPLENISHMENT&from=2026-09-01&to=2026-10-30`
- **Kết quả**: SKU 8 và SKU 20 đứng đầu danh sách với `usable_quantity = 0`, trạng thái `REORDER`. SKU 7 có `usable_quantity = 8`, trạng thái `REORDER`.

### 4.3. Báo Cáo Cảnh Báo Hạn Dùng & Rủi Ro Vốn (Expiry Report)
- **API (7 ngày)**: `GET /api/analytics/operational?report=EXPIRY&from=2026-09-01&to=2026-10-30&horizon=7`
- **API (14 ngày)**: `GET /api/analytics/operational?report=EXPIRY&from=2026-09-01&to=2026-10-30&horizon=14`

### 4.4. Báo Cáo Dòng Tiền & Top Bán Chạy (Cash Flow & Sales)
- **Dòng tiền Tháng 9**: `GET /api/analytics/cash-flow?from=2026-09-01&to=2026-09-30`
- **Dòng tiền Tháng 10**: `GET /api/analytics/cash-flow?from=2026-10-01&to=2026-10-30`
- **Top bán chạy 60 ngày**: `GET /api/analytics/operational?report=SALES&from=2026-09-01&to=2026-10-30&top=20`

### 4.5. Báo Cáo Giá Nhập Theo Lô (Purchase Costs)
- **Giao diện Web**: Menu Sidebar $\rightarrow$ **`Giá nhập theo lô`** (URL: `http://localhost:3000/purchase-costs`).
- **Tháng 9 (`2026-09-01 – 2026-09-30`)**: Hiển thị 17 lô bổ sung giữa kỳ (`SCALE1000`) nhập ngày 20/09/2026.
- **Tháng 10 (`2026-10-01 – 2026-10-30`)**: Hiển thị 17 lô quy mô lớn (`SCALE5000`) nhập ngày 10/10/2026.
- **Trọn 60 ngày (`2026-09-01 – 2026-10-30`)**: Hiển thị toàn bộ 34 lô hàng kèm chi tiết: Mã lô, Đơn giá vốn, So sánh giá với lô liền trước (`price_change`), Số lượng nhập, Lượng còn tồn và Tổng giá trị nhập/tồn.
