# Thay báo cáo bổ sung bằng Thống kê khuyến mãi

Gỡ ba lựa chọn ABC, CHANNELS và QUALITY khỏi OperationalAnalyticsService và màn hình báo cáo. Các báo cáo đang hỗ trợ: SALES, EXPIRY, REPLENISHMENT, PROMOTIONS. Giữ luồng dòng tiền, đối soát, lịch sử kho và giá nhập theo lô.

API mới trong luồng hiện có: `GET /api/analytics/operational?report=PROMOTIONS&from=2026-10-30&to=2026-10-30`.

Mỗi dòng nhóm theo chương trình, tên và mức giảm đã lưu trên hóa đơn cùng sản phẩm. Không JOIN tên/mức giảm hiện tại từ bảng promotions để tính lại lịch sử. Chỉ xuất hiện các chương trình đã thực sự áp dụng trong hóa đơn thuộc khoảng ngày chọn. Số đơn là COUNT DISTINCT trong từng dòng; không cộng số đơn các dòng để gọi là tổng đơn duy nhất vì một hóa đơn có thể dùng nhiều chương trình.

- Đã bán: số lượng gốc trên dòng hóa đơn.
- Đã trả: số lượng đã nhận trả đến lúc truy vấn.
- Bán sau trả: đã bán trừ đã trả.
- Tổng tiền giảm: tổng (giá gốc − giá sau giảm) × số lượng gốc, chưa giảm lại do hàng trả.
- Tiền bán hàng: tổng giá sau giảm × lượng bán sau trả; khác với tiền thực thu nếu còn phiếu chờ hoàn.

Giữ các đơn trả toàn bộ và mức giảm 100% trong thống kê. Khoảng ngày lọc theo thời điểm lập hóa đơn, không phải ngày nhận trả. Không dùng các số này để chứng minh khuyến mãi gây tăng doanh số hoặc tính lợi nhuận.

Backend compile và frontend production build thành công; không chạy bộ test hay thay dữ liệu. API PROMOTIONS thực tế ngày 30/10/2026 trả COMPLETED, 4 nhóm, query ID `151b9626-d420-0436-c2b5-382d28e61f70`. Ngày trống 30/10/2025 trả COMPLETED, 0 dòng. SQL đã lưu tại `drill/multisource/promotions-2026-10-30.sql`. Đây là mốc kiểm tra, dữ liệu có thể đổi khi người dùng thao tác thêm.
