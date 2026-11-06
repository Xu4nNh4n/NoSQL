# Trả hàng POS theo từng món — kế hoạch triển khai

> Thực thi trực tiếp theo `superpowers:executing-plans`, trên nhánh `go-online-tra-hang`. Người dùng đã yêu cầu bắt đầu, không cần vòng phê duyệt bổ sung.

**Goal:** trả đúng số lượng từng món tại quầy, hoàn đúng tiền và đúng tồn lô.
**Architecture:** dùng lại hóa đơn/phiếu trả/payment/outbox, thêm tính phân bổ tiền vào `ReturnPolicy`; dùng chung trang đơn từ POS.
**Tech Stack:** Java/Spring, React/TypeScript; giữ schema MySQL và export Cassandra/MongoDB/Drill.
**Spec:** [thiết kế](tra_hang_thiet_ke.md).

## Ràng buộc và trọng điểm

Không chạy dịch vụ, JVM, build/JUnit, generator hoặc sửa dữ liệu thật. Không commit bí mật. Khóa đơn trước khi kiểm tra tổng lượng/tiền; giữ chống lặp. Kiểm tra số lượng vượt mức, lô không đúng/hết hạn, trả nhiều lần, phần lẻ tiền, đơn online và phiếu cũ vượt tiền thực thu.

## Các bước

- [x] Thêm test Java nguồn: trả một phần, trả hết phần còn lại, vượt số lượng, sai loại đơn, giảm giá/làm tròn, giới hạn biên nhận thu/hoàn và lặp request. Chưa chạy theo giới hạn RAM.
- [x] `ReturnPolicy.refundAmount` thêm phân bổ theo khoảng giá gốc cộng dồn; giữ API bốn tham số cho quy tắc số lượng đã có.
- [x] `SalesOrderServiceImpl`: nhận số lượng tùy chọn, xác minh POS/quyền/tiền, cập nhật trạng thái từng phần và kiểm tra ngân sách hoàn cả lúc nhận và xác nhận phiếu. Giữ khóa/lô/outbox.
- [x] `SalesOrdersPage`: tra mã chính xác, chọn lượng và lô, lý do/tình trạng, thông báo phiếu chờ hoàn; quản lý chỉ xem. `PosPage`: lối vào trả hàng dùng chung trang đơn.
- [x] Generator/API docs/diễn giải báo cáo khớp nghiệp vụ, không sinh lại dataset. Rà tĩnh imports/callers, lint một luồng và cú pháp Python; ghi kết quả và các bước runtime chưa chạy trong [kết quả](tra_hang_ket_qua.md).
- [ ] Khi người dùng cho phép sử dụng RAM: chạy compiler/JUnit, UI/API và đối soát runtime. Chưa nghiệm thu vận hành.

Không tạo commit trong lượt này vì `.git` bị giới hạn ghi theo cấu hình workspace; giữ thay đổi trên nhánh đang có và không gom các sửa đổi cũ không thuộc phạm vi.
