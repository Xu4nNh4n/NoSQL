# Khảo sát thực tế nguồn dunnhumby — The Complete Journey

Nguồn chính thức: https://www.dunnhumby.com/source-files/ . Đã tải archive từ liên kết công khai trong trang, đọc CSV theo luồng và đối chiếu User Guide đi kèm. Chưa nhập hoặc sửa database project.

## Kết quả đọc toàn bộ dữ liệu

| Đại lượng | Kết quả thực tế |
|---|---:|
| Dòng transaction_data.csv | 2.595.732 |
| Dòng product.csv | 92.353 |
| Hộ gia đình | 2.500 |
| Số ngày tương đối | 711, từ DAY 1 đến DAY 711 |
| Dòng có QUANTITY <= 0 | 14.466 |
| Dòng SALES_VALUE = 0 | 18.850 |
| PRODUCT_ID giao dịch không có trong catalog | 0 |

Không cộng các nhóm lỗi vì một dòng có thể thuộc nhiều nhóm. DAY là chỉ số ngày, không tự nhận là ngày lịch Việt Nam. Household là hộ gia đình, không phải một người có tên/số điện thoại.

## Mẫu khảo sát 5.000 giỏ hàng

Lấy 5.000 BASKET_ID khác nhau gặp đầu tiên rồi đọc lại toàn bộ file để lấy đủ dòng thuộc các giỏ đó. Đây là mẫu kiểm tra tính tương thích, không phải mẫu thống kê đại diện.

| Đại lượng | Kết quả |
|---|---:|
| Giỏ hàng | 5.000 |
| Dòng hàng | 49.269 |
| Mã sản phẩm | 16.259 |
| Ngày khác nhau | 54 |
| Dòng số lượng/giá trị không dương | 365 |
| Dòng làm tròn SALES_VALUE/QUANTITY về 2 chữ số làm đổi subtotal | 1.340 |
| Giỏ có ít nhất một vấn đề trong hai tiêu chí trên | 1.208 |
| Giỏ vượt qua hai tiêu chí sơ bộ | 3.792 |
| Cặp BASKET_ID + PRODUCT_ID bị lặp | 0 |

Các phép kiểm tra giá trên mới dùng SALES_VALUE để khảo sát. Chưa phải chứng nhận số tiền này phù hợp báo cáo tiền thực thu.

## Những điều cần chuyển đổi đúng

| Trường nguồn | Cách dùng trong project |
|---|---|
| BASKET_ID | Mã hóa đơn gốc; giữ mapping nếu cấp lại ID nội bộ |
| PRODUCT_ID | SKU gốc; liên kết catalog MongoDB và sự kiện Cassandra |
| QUANTITY | Số lượng; loại/giải thích dòng không dương, không tự biến thành phiếu trả |
| DAY, TRANS_TIME | Ngày tương đối và giờ; cần quy tắc ánh xạ sang lịch đã công bố |
| SALES_VALUE | Theo User Guide là số tiền nhà bán lẻ nhận, không luôn là tiền khách trả khi có coupon |
| RETAIL_DISC, COUPON_DISC, COUPON_MATCH_DISC | Phải xử lý theo User Guide; không bỏ qua rồi coi SALES_VALUE là tiền thực thu |
| COMMODITY_DESC, SUB_COMMODITY_DESC | Nhóm và nhóm chi tiết; nguồn không có tên sản phẩm đầy đủ như catalog tiếng Việt hiện tại |
| CURR_SIZE_OF_PRODUCT | Quy cách đóng gói nếu có; có thể lưu thuộc tính MongoDB |
| household_key | Mã hộ gia đình ẩn danh; không phải tài khoản khách hoặc danh tính thật |

User Guide nêu SALES_VALUE là tiền nhà bán lẻ nhận, có thể bao gồm khoản được nhà sản xuất bù coupon. Đây là khác biệt quyết định khi dùng cho dòng tiền của POS.

## Đề xuất cho bước tiếp theo

1. Giữ bộ mẫu hiện tại đến khi chốt quy tắc chuyển đổi.
2. Chọn giỏ hàng phù hợp nghiệp vụ đang hỗ trợ: số lượng/giá dương, xử lý được giảm giá và subtotal. Cách đơn giản là chọn giỏ không có coupon/discount rồi kiểm tra lại khả năng lấy đủ 5.000 giỏ trong toàn nguồn.
3. Giữ toàn bộ dòng của mỗi giỏ được chọn. Nếu chỉ lấy một số nhóm hàng, công bố rõ đó là phần giao dịch thuộc các nhóm đã chọn và tính lại tổng; không gọi là tổng hóa đơn gốc.
4. Tạo nhãn hiển thị từ nhóm + quy cách + PRODUCT_ID và ghi rõ đây là nhãn dựng lại; không thay sản phẩm nguồn bằng các tên Việt không liên quan.
5. Giữ đơn vị tiền và số tiền gốc; chỉ đổi tiền/ngày khi có quy tắc được ghi rõ. Các lô, hạn dùng, nhà cung cấp và sự kiện vận hành bổ sung phải đánh dấu là dữ liệu tạo thêm.
6. Nhập thử tập nhỏ ở môi trường riêng, đối chiếu số dòng, tổng tiền và SKU trước khi thay dữ liệu ứng dụng.

Archive, SHA-256, hai file mẫu CSV, kết quả đếm và bản trích User Guide được lưu cục bộ tại `C:/Users/Admin/supermarket-run/dataset-check/`. Chưa đưa dữ liệu gốc lên Git hoặc gắn nguồn này vào bộ dữ liệu đang dùng.
