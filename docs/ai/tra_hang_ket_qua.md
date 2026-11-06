# Kết quả sửa trả hàng POS

Ngày 2026-10-28, nhánh `go-online-tra-hang`. Đã thay đổi source theo [thiết kế đã chốt](tra_hang_thiet_ke.md); chưa nghiệm thu bằng ứng dụng/database chạy thật vì giới hạn RAM.

## Thu ngân sử dụng thế nào

Tại POS, bấm **Tra hóa đơn / Trả hàng** → nhập **Mã hóa đơn gốc** → **Tra hóa đơn** → kiểm tra chi tiết và bấm **Trả hàng**. Nhập số lượng thực nhận ở món cần trả, để 0 ở món khách giữ lại; điền lý do, tình trạng và lô thực tế khi cần. Bấm **Xác nhận đã nhận hàng** để tạo phiếu. Chi tiết mở lại với phiếu **Chờ hoàn tiền**; thu ngân hoàn thực tế theo phương thức đã thu rồi dùng nút xác nhận hoàn tiền đang có.

Ví dụ: mua 3 hộp sữa và 2 gói bánh, trả 1 hộp sữa. Chỉ 1 hộp sữa cập nhật vào hàng trả; 2 hộp sữa và 2 gói bánh còn được trả về sau. Đơn là `PARTIALLY_RETURNED`; khi trả hết các món còn lại mới thành `RETURNED`.

## Đã sửa và dọn

- API chỉ nhận trả/hoàn mới cho POS và kiểm tra lại quyền `ADMIN`/`STAFF`; quản lý xem được nhưng không thấy nút ghi. Bỏ chấp nhận trạng thái giao hàng online trong trả hàng.
- Bỏ kiểm tra bắt trả đủ mọi dòng/toàn bộ số lượng. Khóa đơn, kiểm tra lượng cộng dồn, giữ request key để nhận lại cùng yêu cầu không cộng kho hai lần.
- Tính tiền từ đơn giá đã lưu và tổng thực trả sau giảm giá. Các khoảng giá gốc cộng dồn theo ID dòng phân bổ hết phần lẻ đúng một lần; không dùng giá catalog hiện tại.
- Trước khi nhận trả: kiểm tra tiền thực thu, hoàn đã ghi và phiếu đang chờ. Khi xác nhận phiếu: kiểm tra tiền còn có thể hoàn một lần nữa. Phiếu đã hoàn phải có biên nhận khớp; thiếu/sai thì chặn để đối soát, không tự đoán số tiền.
- Hàng bán lại chỉ về lô gốc hợp lệ; hàng không bán lại tăng lượng hỏng/cách ly, không tăng tồn bán được. Giữ các chốt lô hết hạn/sai lô đã có.
- Form có lượng đã mua/đã trả/còn được trả, lượng nhập mặc định 0 và lựa chọn lô. Tra đúng mã gọi API trực tiếp, không phụ thuộc 100 đơn đang tải. Tìm kiếm tổng quát trong bảng vẫn chỉ áp dụng cho các đơn đã tải.
- Giữ một nút xác nhận hoàn tiền; chống bấm lặp bằng khóa thao tác và mã biên nhận ổn định theo phiếu. Bỏ nhãn/cột giao hàng, kênh bán cố định và form “trả toàn bộ” không còn dùng.
- Bỏ nhánh COD/giao hàng thừa trong hủy POS và overload `releaseInventory` luôn được gọi với cùng giá trị. Giữ giải phóng reservation lịch sử.
- Formatter tiền hiển thị tối đa hai số thập phân khi thực sự có phần lẻ, khớp `DECIMAL(18,2)` đang dùng; tiền nguyên giữ cách hiển thị cũ.
- Generator ghi trạng thái `PARTIALLY_REFUNDED` khi đã hoàn cho đơn trả một phần. Không chạy generator hoặc đổi dataset đã có.

## Kiểm tra đã chạy

- Rà tĩnh 154 file Java: import nội bộ/cặp ngoặc, tên test và khai báo service; 33 file TS/TSX: import tương đối; kiểm tra cú pháp Python generator. Các kiểm tra tĩnh không thay thế compiler/typecheck.
- Oxlint một luồng trên các file frontend trong phạm vi: không có lỗi; còn bốn warning có sẵn ở hai effect tải dữ liệu của trang POS/đơn (`set-state-in-effect`, `exhaustive-deps`).
- 16 assertion chạy trực tiếp các helper frontend thật: điều kiện trả POS, lượng lô, lô hết hạn/sai trạng thái và formatter tiền. Dùng Node với heap 32 MiB, không khởi chạy React hoặc dịch vụ. Node có warning experimental cho API bỏ kiểu TypeScript dùng trong script kiểm tra.
- Mô hình Decimal độc lập kiểm tra 1.500 hóa đơn với số lượng, giảm giá và nhiều lần trả ngẫu nhiên: tổng phân bổ bằng đúng số tiền đã thu. Đây là kiểm tra bất biến toán học, không phải chạy code Java.
- `git diff --check` trên các file trong phạm vi; chỉ cảnh báo LF/CRLF, không có lỗi whitespace.

Đã thêm/cập nhật test Java nguồn cho trả một phần/trả hết phần còn lại, món hỏng, giá gốc sau giảm giá, lô thực tế/hết hạn, vượt lượng, thiếu/vượt tiền, phân quyền, đơn online và chống nhận/hoàn lặp. **Chưa chạy JUnit, Maven compile, frontend build/typecheck, kiểm thử giao diện, HTTP/API, truy vấn DB hoặc SQL Drill.** Không dùng kết quả cũ để khẳng định bản mới xanh.

## Giới hạn cần biết

Giữ schema mỗi mặt hàng một dòng trong một phiếu: nếu cùng món trả từ nhiều lô/tình trạng không thể gộp an toàn, lập các phiếu riêng. Không bổ sung hạn trả tự động hoặc quy trình quản lý duyệt. Đơn lịch sử thiếu biên nhận thu/hoàn hoặc nguồn gốc lô cần được đối soát; không bỏ các kiểm tra để ép xử lý thành công. Báo cáo số lượng bán trừ lượng trả, còn giá trị món trong báo cáo vận hành vẫn theo đơn giá gốc như phần diễn giải; dòng tiền dùng tiền thu/hoàn thực tế.

Không thay đổi dữ liệu thật, migration, JSON/Parquet, `.env` hoặc tạo database mới. Thay đổi vẫn nằm trong worktree có nhiều sửa đổi trước đó; chưa commit thêm do `.git` bị giới hạn ghi. Bước sau khi đủ RAM là chạy compiler/JUnit, kiểm tra các trường hợp nêu trên qua UI/API và đối soát payment/outbox/export trước khi dựng dataset demo sạch.
