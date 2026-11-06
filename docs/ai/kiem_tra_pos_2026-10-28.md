# Rà soát POS sau khi bỏ Shop online — 2026-10-28

Cập nhật sau khi người dùng chốt: đã sửa source trả từng món, phân bổ tiền hoàn/giới hạn biên nhận, chặn đơn online và nút trả/hoàn cho quản lý; thêm tra cứu chính xác mã hóa đơn. Xem [kết quả triển khai](tra_hang_ket_qua.md). Các nhận xét bên dưới là mốc trước khi sửa; phân trang tổng quát, cấp mã lô đồng thời và hàng đợi refresh token chưa xử lý trong lượt trả hàng.

## Phạm vi và kết luận

Rà tĩnh luồng tạo đơn POS → xuất lô FEFO → thanh toán → outbox, nhập/giảm kho, trả hàng/hoàn tiền, quyền truy cập, frontend và báo cáo. Giả định ứng dụng phục vụ một cửa hàng với nhiều nhân viên có thể thao tác đồng thời. Không khởi chạy ứng dụng, JVM, Docker, database, Drill, build hay JUnit theo yêu cầu hạn chế RAM. Không truy vấn hoặc thay đổi dữ liệu thật; không sinh lại dataset.

**Chưa đủ cơ sở kết luận nghiệp vụ hoàn chỉnh.** Phần POS/kho có các chốt kiểm tra cần thiết trong source, nhưng thiết kế trả hàng chưa được chốt, frontend còn hạn chế tra cứu và bộ dữ liệu mô phỏng chưa đồng nhất với nghiệp vụ hiện tại.

## Đã dọn/sửa trong lượt này

- Gỡ năm method online không còn controller/caller sản xuất: `paySalesOrder`, `shipSalesOrder`, `deliverSalesOrder`, `failDelivery`, `getMyOnlineOrders`; gỡ khai báo interface, query `findMyOnlineOrders`, helper `deductReservedInventory`, `consumeReserved` và các test chỉ dành cho những method đã xóa. Đã tìm lại trên source/test/frontend để tránh caller bị bỏ sót.
- Thu gọn `createOrder`: chỉ còn `IN_STORE`, tiền mặt/chuyển khoản tại quầy, xuất lô và ghi thanh toán ngay trong transaction. Bỏ nhánh tạo reservation mới, COD, thông tin người nhận và địa chỉ giao khỏi request. Không đổi quy tắc giá, tồn hoặc hoàn tiền.
- Bỏ state địa chỉ/người nhận/kênh online khỏi giỏ React; giữ giỏ vì POS vẫn dùng. Trang đăng nhập bỏ nút về Shop và tài khoản khách mẫu; `WAREHOUSE` được chuyển đúng tới `/inventory`.
- `ensureOrderAccess` cho phép `ADMIN`, `MANAGER`, `STAFF`, khớp quyền đọc chi tiết đơn ở controller; bỏ quyền sở hữu đơn dành cho khách online. `SecurityConfig` bỏ catalog công khai và whitelist đăng ký khách, giới hạn API vận hành cho bốn vai trò nội bộ. Controller vẫn kiểm tra quyền thao tác riêng; chỉ `STAFF` được tạo đơn qua HTTP.
- Bỏ cấu hình thời hạn thanh toán mới không còn nơi dùng; vẫn giữ lịch quét giải phóng reservation của đơn lịch sử. Generator bỏ các nhánh COD/giao hàng không thể đi tới; không chạy generator. Diễn giải báo cáo sửa thành POS, khớp bộ lọc SQL `IN_STORE` đang có.
- Thêm test nguồn cho quản lý đọc đơn, khách không đọc đơn nội bộ, truy cập catalog không token/khách cũ bị chặn; đổi test tạo/hủy online thành kỳ vọng từ chối. Sửa hai fixture POS cũ cho tồn khả dụng và mã đơn đơn giản. Các test Java này **chưa chạy**.

Giữ entity/response/migration và nhãn trạng thái lịch sử, trường reserved, `releaseReserved`, job hết hạn, event cũ và JSON/Parquet. Chưa reset database. Thân các hàm `returnSalesOrder`, `confirmReturnRefund`, `confirmRefund`, `receiveReturn` không sửa; chính sách trả hàng vẫn chờ người dùng chốt.

## Các điểm chưa hoàn tất

| Mức | Vấn đề và trường hợp cụ thể | Cách xử lý tiếp |
|---|---|---|
| Cần xử lý trước khi chốt trả hàng | `SalesOrderServiceImpl.returnSalesOrder` còn chấp nhận trạng thái lịch sử `DELIVERED`/`RETURN_IN_TRANSIT` và không bắt buộc toàn bộ đơn phải là POS; biết ID của đơn online cũ vẫn có thể gọi nghiệp vụ trả bằng tài khoản nội bộ. `confirmReturnRefund` cũng chưa giới hạn kênh POS. | Chốt thiết kế trả hàng rồi giới hạn cả nhận trả và hoàn phiếu ở POS; không sửa chính sách khi người dùng chưa chọn. |
| Cần xử lý với đơn giảm giá cũ | Hoàn tiền tính `unit_price × quantity`, chưa trừ giảm giá cấp đơn. Ví dụ mua 2 × 10.000, giảm 5.000, thu 15.000: trả cả đơn hiện tính 20.000. Đơn POS mới hiện luôn có giảm giá bằng 0, nhưng dữ liệu cũ có giảm giá vẫn rủi ro. | Giá hoàn phải dựa số đã thanh toán/phân bổ giảm giá và chặn tổng hoàn vượt tiền đã thu; đưa vào thiết kế trả hàng. |
| Chính sách chưa chốt | Code hiện bắt trả tất cả mặt hàng còn lại và đủ số lượng từng dòng; chưa có thời hạn trả hay bước duyệt riêng. Điều này khác phương án trả từng món/từng số lượng đã được đề xuất. | Chốt điều kiện trả, giá hoàn, cách nhận hàng bán lại/hỏng, lô, quyền duyệt và hình thức hoàn. |
| Cần sửa frontend | `SalesOrdersPage` tải `page=0&size=100`, tìm kiếm/lọc trên mảng đã tải. Đơn thứ 101 trở đi không được tra cứu qua ô tìm kiếm hiện tại. | Tìm/lọc bằng API và có phân trang, không tải toàn bộ lịch sử vào RAM. |
| Cần đồng bộ quyền giao diện trả | `MANAGER` vào được trang đơn và thấy nút trả/hoàn tiền, nhưng endpoint ghi chỉ nhận `ADMIN`/`STAFF`; bấm nút sẽ 403. | Chỉ hiện nút đúng vai trò trong lượt thiết kế trả hàng; không tự mở thêm quyền hoàn tiền cho quản lý. |
| Cần sửa khi nhiều người nhập hàng | Mã lô tự sinh dùng `MAX(batch_code)+1`, còn khóa hiện tại theo từng sản phẩm. Hai yêu cầu nhập hai SKU khác nhau có thể chọn cùng mã lô; unique constraint khiến một yêu cầu thất bại/rollback. | Dùng bộ cấp mã được khóa chung hoặc số ID; không bỏ unique constraint. Chưa tái hiện bằng chạy đồng thời. |
| Cần sửa xử lý phiên | `api/client.ts` xếp request chờ refresh token vào callback. Nếu refresh thất bại, code xóa callback mà không reject các Promise đang đợi; một số màn hình có thể tiếp tục loading. | Kết thúc cả thành công và thất bại cho từng request đang chờ; kiểm thử đồng thời khi có RAM. |
| Dữ liệu mô phỏng cần làm lại sau nghiệp vụ | Generator còn tạo trả một phần và ghi `payment_status='PAID'` cho đơn trả một phần dù đã ghi REFUND; backend hiện chỉ nhận trả toàn bộ. | Chốt nghiệp vụ rồi điều chỉnh generator và sinh dataset phiên bản mới. Không sửa/xóa dataset đang có để che khác biệt. |

## Những chốt đã thấy trong source

Tạo đơn có transaction, gộp lượng mua và khóa sản phẩm theo thứ tự ID, kiểm tra sản phẩm hoạt động/tồn lô khả dụng trước xuất. FEFO bỏ lô hết hạn, ưu tiên hạn gần nhất rồi thời điểm nhập; lô không hạn xếp sau. Giảm tồn lô cho phép về 0 nhưng không thấp hơn phần đã giữ. Tồn có thể bán loại phần đã giữ, lô hết hạn/cách ly và sản phẩm bị ẩn. Cờ giải phóng kho cùng khóa đơn giúp tránh nhả hai lần. Outbox nằm trong cùng transaction nghiệp vụ. Đây là nhận xét từ source, chưa phải kết quả chạy nhiều người dùng hoặc database.

## Xác minh nhẹ đã thực hiện

- Python quét 154 file Java: import nội bộ tồn tại, cặp ngoặc cân bằng; 12 khai báo service có implementation; không còn tên method online đã gỡ trong source/test. Quét 33 file TS/TSX: import tương đối trỏ tới file tồn tại. Các kiểm tra này không thay Java/TypeScript typecheck.
- Oxlint chạy một luồng trên ba file frontend thay đổi: exit 0, một warning Fast Refresh về việc `CartContext.tsx` cùng export provider/hook. Không có lỗi cú pháp được linter báo.
- Phân tích cú pháp Python generator mà không thực thi và kiểm tra whitespace Git trên các file trong phạm vi.
- Không chạy Maven compile/JUnit, frontend build/typecheck đầy đủ, HTTP/API, truy vấn DB, SQL Drill hoặc đối soát runtime. Không dùng kết quả chạy cũ để khẳng định bản mới PASS.

## Thứ tự tiếp theo

Chốt thiết kế trả hàng POS → sửa các điểm nghiệp vụ/giao diện liên quan → đồng bộ generator/seed → chạy build/test và đối soát khi đủ RAM → mới dựng bộ database/dataset demo sạch nếu vẫn muốn bỏ dữ liệu cũ. Rà tĩnh không đủ để tuyên bố hệ thống ổn định hoàn toàn.
