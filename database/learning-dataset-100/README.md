# Bộ Dữ Liệu Học Tập 100 Hóa Đơn POS & BigData Analytics (Lịch Sử 30 Ngày)

Tài liệu hướng dẫn và đặc tả kỹ thuật cho bộ dữ liệu học tập 108 giao dịch POS tại quầy, phân bổ trải rộng **30 ngày (01/10/2026 – 30/10/2026)**, đồng bộ đa nguồn **MySQL – Cassandra – JSON** và đối soát qua **Apache Drill**.

---

## 1. Nguồn Gốc và Tuyên Bố Dữ Liệu

- **Phân loại**: Dữ liệu học tập và kiểm thử tích hợp do nhóm xây dựng (*Learning Dataset*).
- **Hành vi khách hàng thực tế**:
  - **86.1% hóa đơn (93/108 đơn)** là **khách vãng lai không tên** (`customer_name = NULL, customer_phone = NULL`), phản ánh đúng đặc thù siêu thị bán lẻ: khách vào quầy chọn hàng, quét barcode, thanh toán (tiền mặt/chuyển khoản), lấy hóa đơn rồi đi.
  - **13.9% hóa đơn (15/108 đơn)** có tên khách hàng thành viên/thân thiết thông thường.
  - Tuyệt đối không chứa tiền tố/hậu tố nhân tạo như `MẪU`, `TEST`, `DEMO`, `SEED`, `SIM`.
- **Tính toàn vẹn nghiệp vụ**:
  - Hóa đơn tạo qua quy trình POS chính thống: `POST /api/orders/quote` $\rightarrow$ nhận `quoteToken` $\rightarrow$ `POST /api/orders` bằng tài khoản STAFF.
  - Phân bổ kho tự động theo chính sách **FEFO** (`FefoPolicy`), trừ tồn theo lô vật lý (`inventory_batches`) và lưu `batch_allocations` trên từng dòng hóa đơn.
  - Snapshot khuyến mãi phần trăm thời điểm bán (`PromotionSnapshot`).
  - Giao dịch trả hàng và hoàn tiền tuân thủ `ReturnPolicy`.
  - Nhật ký thu/hoàn được phát sinh qua cơ chế Outbox (`data_sync_outbox`) và đồng bộ sang Cassandra (`business_events_by_day`) cùng JSON payment log (`analytics/pos-payments/<ngày UTC>/<eventId>.json`).

---

## 2. Quy Mô và Phân Bổ 30 Ngày

| Đối tượng | Quy mô | Chi tiết |
|---|---|---|
| **Khoảng thời gian** | **30 ngày** | Từ **01/10/2026** đến **30/10/2026** (giờ mở cửa: 08:30 – 21:00 VN). |
| **Tổng hóa đơn POS** | **108 đơn** | Trung bình 3.6 đơn/ngày, phân bổ đa dạng giữa các ngày và khung giờ. |
| **Khách vãng lai (ẩn danh)** | **93 đơn (86.1%)** | Khách thanh toán lấy hóa đơn ra về (`customer_name = NULL`). |
| **Khách có tên** | **15 đơn (13.9%)** | Khách thân thiết có lưu thông tin. |
| **Nhà cung cấp** | **6 đơn vị** | 6 công ty cung ứng đa dạng mặt hàng. |
| **Danh mục sản phẩm** | **20 SKU** | 8 nhóm hàng thiết yếu (Đồ uống, Sữa, Đồ khô, Gia vị, Bánh kẹo, Cá nhân, Vệ sinh, Gia dụng). |
| **Lô nhập kho** | **30 lô hàng** | Nhập đầu kỳ và bổ sung giữa kỳ, tuân thủ chặt chẽ FEFO. |
| **Cassandra Business Events** | **509 sự kiện** | Đầy đủ `INVENTORY_RECEIVED`, `ORDER_CREATED`, `PAYMENT_RECEIVED`, `RETURN_RECEIVED`, `REFUND_CONFIRMED`. |
| **Cassandra Stock Snapshots** | **580 bản ghi** | Snapshot tồn kho cuối ngày (`stock_daily_by_day`) cho 29 ngày đã kết thúc. |
| **JSON Payment Logs** | **113 file log** | Lưu theo từng phân vùng ngày tại `analytics/pos-payments/<ngày UTC>/`. |
| **Đối soát Drill 30 ngày** | **30/30 ngày PASS** | Chênh lệch tiền thu `0.0`, hoàn `0.0`, tiền dòng hàng `0.0`. |

---

## 3. Các Script Trong Thư Mục

1. **[generate-dataset.py](file:///D:/code/Code/Java/Backend/SuperMarketAPI-BigData/database/learning-dataset-100/generate-dataset.py)**:
   - Sinh danh mục sản phẩm, nhà cung cấp, lô kho, khuyến mãi và nạp 100 hóa đơn POS thông qua API thật của hệ thống.
   - Có cơ chế Journaling, Lock file chống chạy đồng thời, tự động dispatch Outbox và kiểm tra Drill.

2. **[redistribute-30days.py](file:///D:/code/Code/Java/Backend/SuperMarketAPI-BigData/database/learning-dataset-100/redistribute-30days.py)**:
   - Phân bổ toàn bộ 108 hóa đơn POS, lô hàng, phiếu trả hàng và thanh toán trải rộng 30 ngày lịch sử.
   - Thiết lập tỷ lệ 86% khách vãng lai không tên theo yêu cầu.
   - Đồng bộ lại Outbox payload, Cassandra event store và các file JSON payment log.
   - Gọi API sinh snapshot kho Cassandra cuối ngày (`stock_daily_by_day`) và đối soát qua Drill.

---

## 4. Hướng Dẫn Kiểm Tra Trên Giao Diện & API

1. **Kiểm tra hóa đơn POS**:
   - Mở giao diện POS hoặc API `GET /api/orders?page=0&size=20`.
   - Các hóa đơn trải dài từ đầu tháng 10 đến ngày 30/10/2026.
   - Đa số hóa đơn hiển thị khách vãng lai (không có tên), một số ít có tên.
2. **Kiểm tra đối soát 30 ngày (Reconciliation)**:
   - Gọi `GET /api/analytics/reconciliation?from=2026-10-01&to=2026-10-30` (tài khoản `admin`).
   - Kết quả: **Tất cả 30 ngày đều đạt chuẩn `PASS`** (QueryId Drill: `151b8d45-9ad9-911a-ad1d-22e3017fabf1`).
3. **Kiểm tra snapshot tồn kho Cassandra**:
   - Truy vấn Drill: `SELECT * FROM cassandra_source.supermarket_analytics.stock_daily_by_day LIMIT 20;`
   - Ghi nhận 580 bản ghi snapshot closing balance cho các ngày đã kết thúc.
4. **Kiểm tra báo cáo vận hành**:
   - Bán chạy: `GET /api/analytics/operational?report=SALES&from=2026-10-01&to=2026-10-30`
   - Cảnh báo hạn dùng: `GET /api/analytics/operational?report=EXPIRY&from=2026-10-01&to=2026-10-30&horizon=14`
   - Gợi ý nhập hàng: `GET /api/analytics/operational?report=REPLENISHMENT&from=2026-10-01&to=2026-10-30`
   - Khuyến mãi: `GET /api/analytics/operational?report=PROMOTIONS&from=2026-10-01&to=2026-10-30`
   - Dòng tiền: `GET /api/analytics/cash-flow?from=2026-10-01&to=2026-10-30`
