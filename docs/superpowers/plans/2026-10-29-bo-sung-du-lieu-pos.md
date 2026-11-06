# Kế hoạch bổ sung dữ liệu POS theo hai đợt

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Chỉ triển khai nạp dữ liệu khi người dùng yêu cầu tiếp tục thực hiện.

**Goal:** Bổ sung khoảng 1.000 bản ghi nghiệp vụ trước, sau đó mở rộng lên khoảng 5.000 khi dữ liệu và báo cáo đã được đối chiếu.

**Architecture:** MySQL lưu nghiệp vụ gốc. Dùng cơ chế outbox hiện có để đồng bộ catalog sang MongoDB và lịch sử kho/thanh toán sang Cassandra; xuất sự kiện cho Apache Drill đọc bằng luồng hiện có. Không tạo database SIMULATED, không tạo nguồn sự kiện độc lập thiếu chứng từ MySQL.

**Tech Stack:** MySQL, Spring Boot, MongoDB local, Cassandra local, Apache Drill.

**Spec:** Yêu cầu trong hội thoại: kế hoạch trước, bổ sung sản phẩm và dữ liệu liên quan trực tiếp vào database hiện có; đợt đầu khoảng 1.000 bản ghi rồi bổ sung phần còn lại.

## Ràng buộc

- Dữ liệu được tạo mới là dữ liệu mẫu sát nghiệp vụ thực tế, không phải giao dịch lịch sử được thu thập từ cửa hàng thật. Ghi rõ nguồn tạo trong ghi chú chứng từ.
- Bổ sung, không xóa dữ liệu hiện có; giữ tài khoản và quyền. Không sửa schema hoặc thêm bảng chỉ để nạp dữ liệu.
- Không thêm shop online, giao hàng, COD, tích điểm hoặc khuyến mãi.
- SKU và mã lô ngắn, duy nhất; kiểm tra mã hiện có trước khi cấp tiếp. Không mặc định ID bắt đầu từ 1.
- Không khởi chạy thêm dịch vụ ở bước lập kế hoạch. Không chạy bộ test tự động theo yêu cầu trước của người dùng; khi thực hiện sẽ đối chiếu dữ liệu và API đọc.

## Quy mô đợt đầu

Đây là số lượng dự kiến bổ sung, không phải tổng số sau khi nạp. Không cộng bản sao MongoDB/Cassandra hoặc hàng đợi outbox vào số bản ghi nghiệp vụ.

| Bảng MySQL | Số lượng dự kiến | Nội dung |
|---|---:|---|
| products | 50 | Đồ uống, sữa, mì/gạo, bánh kẹo, gia vị, thực phẩm đóng hộp, vệ sinh cá nhân, chất tẩy rửa, gia dụng, trái cây đóng gói |
| suppliers | 5 | Nhà cung cấp với thông tin liên hệ mẫu, phân phối nhiều sản phẩm |
| inventory_batches | 75 | Mỗi sản phẩm có 1–2 lô, khác ngày nhập/hạn dùng khi phù hợp |
| sales_orders | 110 | Hóa đơn POS tiền mặt hoặc chuyển khoản, phân bố trong 30 ngày |
| sales_order_items | 275 | Khoảng 2–3 dòng mỗi hóa đơn, mỗi dòng có số lượng phù hợp |
| sales_order_returns | 10 | Trả một phần, có hàng bán lại được và hàng hỏng; 6 phiếu hoàn tiền, 4 phiếu chờ hoàn |
| payment_records | 116 | 110 khoản thu và 6 khoản hoàn tiền |
| stock_movements | Khoảng 360 | Lịch sử nhập, xuất bán và nhận trả theo cách ghi thực tế của service |
| **Tổng** | **Khoảng 1.001** | Số lịch sử kho có thể khác nếu service ghi tách theo lô |

MongoDB nhận catalog của các sản phẩm bổ sung, kèm thuộc tính theo nhóm hàng; ảnh để trống để người dùng bổ sung sau. Cassandra nhận sự kiện từ chứng từ kho và thanh toán đã tồn tại ở MySQL. Không ấn định số sự kiện bằng số hóa đơn vì một hóa đơn có thể phát sinh nhiều sự kiện.

## Điểm cần kiểm soát

- Không bán trước ngày nhập hoặc sau hạn dùng; cấp phát theo FEFO và lưu batch_allocations đúng cấu trúc Java hiện tại.
- Không trả vượt số đã mua; tiền hoàn dùng giá trên dòng hóa đơn, không dùng giá catalog hiện tại.
- Hàng hỏng không tăng tồn bán được; hàng nhập lại phải vào đúng lô và đáp ứng điều kiện hạn dùng.
- Phân biệt tiền hoàn đã chi với phiếu trả đang chờ hoàn; không ép doanh thu ròng bằng tiền thu ròng.
- Ngày báo cáo là giờ Việt Nam; xuất đủ các phân vùng UTC liên quan, kể cả ngày không có sự kiện.

## Các bước thực hiện

### 1. Chụp mốc và chuẩn bị tập dữ liệu

- [ ] Đọc lại SQL/schema.sql, entity và các service bán hàng, nhập kho, trả hàng trước khi viết script; kiểm tra schema thật không lệch source.
- [ ] Đếm dữ liệu hiện có, chụp tổng tiền/tồn kho và sao lưu dữ liệu sẽ bị ảnh hưởng; không lưu mật khẩu vào script hoặc báo cáo.
- [ ] Tạo một script riêng trong database/seed-pos-operations.py, không dùng bộ sinh SIMULATED; tái sử dụng công cụ kết nối hiện có, tránh thêm thư viện nếu không cần.
- [ ] Script có bước xem trước số lượng/mã/chứng từ trước khi ghi; nhận diện đợt nạp để chạy lại không tạo trùng và dừng nếu phát hiện đợt nạp dở.

### 2. Bổ sung sản phẩm, nhà cung cấp và kho

- [ ] Lập danh sách 50 tên sản phẩm cụ thể, không dùng tên dạng Sản phẩm 001; giá VND và đơn vị đóng gói phù hợp hệ thống số lượng nguyên.
- [ ] Tạo 5 nhà cung cấp và 75 lô có giá nhập, ngày nhập/hạn dùng hợp lý. Hàng gia dụng không bắt buộc hạn dùng.
- [ ] Dự kiến một số sản phẩm hết hàng, gần ngưỡng nhập hoặc gần hết hạn sau bán để báo cáo có tình huống sử dụng; không sửa số tồn tùy tiện để tạo biểu đồ.

### 3. Bổ sung giao dịch liên kết

- [ ] Tạo theo thứ tự thời gian: nhập kho → bán → nhận trả → hoàn tiền. Nếu cần ghi lịch sử 30 ngày, dùng giao dịch SQL với cấu trúc/status đã đối chiếu service; API hiện tại không được giả định hỗ trợ ngày quá khứ.
- [ ] Ghi đồng bộ hóa đơn, dòng hàng, phân bổ lô JSON, tồn kho, phiếu trả và biên nhận; lỗi thì rollback đơn vị giao dịch, không tắt khóa ngoại.
- [ ] Đọc lại các phương trình tồn và tiền sau nạp; chỉ tiếp tục đồng bộ khi nguồn MySQL nhất quán.

### 4. Đồng bộ và đối chiếu báo cáo

- [ ] Tái sử dụng DataSyncOperations.enqueueCatalog(), enqueueHistory() và bộ dispatch hiện có; không tự chế event_type hoặc nhân đôi sự kiện.
- [ ] Kiểm tra SKU/thuộc tính MongoDB, khóa sự kiện Cassandra và hàng đợi lỗi; chờ đồng bộ xong rồi xuất các ngày báo cáo.
- [ ] Truy vấn Drill: bán chạy theo số lượng, tồn/hạn dùng, dòng tiền và đối soát theo từng khoảng tối đa hiện được API hỗ trợ.
- [ ] Ghi số lượng thực nạp, tổng thu/hoàn/tồn và chênh lệch từng nguồn; báo nguyên nhân nếu không khớp, không sửa số để ép PASS.

### 5. Mở rộng sau đợt đầu

- [ ] Người dùng xem giao diện với khoảng 1.000 bản ghi trước; điều chỉnh nhóm hàng, mức tồn và số ngày nếu cần.
- [ ] Bổ sung lên khoảng 5.000 bản ghi nghiệp vụ bằng cùng cách nạp chống trùng, ưu tiên tăng hóa đơn/lịch sử để phân tích thay vì tăng vô tội vạ số sản phẩm.

## Trạng thái

Đã đọc schema SQL và cơ chế đồng bộ lịch sử trong source để lập kế hoạch. Chưa tạo script, chưa kết nối kiểm kê database thật, chưa nạp dữ liệu và chưa chạy báo cáo cho đợt này.
