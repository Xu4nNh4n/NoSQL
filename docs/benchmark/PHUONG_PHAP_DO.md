# Đo Apache Drill theo số hóa đơn POS

## Thiết kế thực nghiệm

- Bốn mốc là **tổng số hóa đơn**: 1.000, 3.000, 5.000, 7.000. Số dòng hàng được đếm riêng; không gọi 1.000 hóa đơn là 1.000 dòng dữ liệu.
- Cùng khoảng ngày Việt Nam 30/09–29/10/2026. Dữ liệu mẫu được trải ngày sau mỗi lần bổ sung, giữ thứ tự nghiệp vụ gốc và cập nhật cùng MySQL/outbox/Cassandra.
- Hai báo cáo: SALES (MySQL + MongoDB, Top 20 theo số lượng bán ròng) và CASH_FLOW (bản xuất Cassandra, 30 dòng theo ngày).
- SQL của mỗi loại báo cáo phải giống hệt giữa các mốc; trình dựng báo cáo sẽ dừng nếu phát hiện khác SQL, khoảng ngày hoặc cấu hình đo.
- Cùng Drill container: giới hạn 3 GiB, heap 1 GiB, direct memory 1 GiB; width.max_per_node=1, hashjoin.num_partitions=4. Cấu hình phần cứng ghi ở environment.json.
- Chờ outbox hết trước khi đo. Backend tạm dừng khi chỉnh thời gian; bật lại và xuất đủ ngày trước benchmark. Không khởi động lại Drill hay xóa cache giữa các mốc.
- Gọi API trước để lấy SQL, sau đó một lượt warm-up và ba lượt đo tuần tự qua Drill REST. Lưu cả lượt warm-up, nhưng không đưa vào trung vị/min/max.

## Ý nghĩa thông số

| Thông số | Cách đọc |
|---|---|
| profileTotalMs | end - start trong Drill Profile |
| planningMs | planEnd - start |
| executionAfterQueueMs | end - max(queueWaitEnd, planEnd) |
| httpElapsedMs | Thời gian phía client chờ request Drill; gồm độ trễ HTTP/serialize nên khác thời gian Profile |
| maxFragmentPeakBytes | Giá trị maxMemoryUsed lớn nhất trong các minor fragment; **không phải tổng RAM hoặc đỉnh RAM toàn container** |
| totalFragments | Số fragment ghi trong Profile; không đồng nghĩa số máy chủ |
| rows | Số dòng kết quả; Top 20 có 20 dòng không có nghĩa chỉ xử lý 20 dòng nguồn |
| Total Cost trên ảnh | Ước lượng chi phí của bộ tối ưu, không phải tiền và không phải thời gian đo |

Các thành phần thời gian trong bảng tổng hợp lấy trung vị riêng, nên không nhất thiết cộng đúng bằng trung vị thời gian tổng. Ảnh Profile minh chứng được chọn từ lượt có tổng thời gian gần trung vị nhất, không chọn tùy ý lượt nhanh nhất.

Nếu có lượt thất bại, giữ nguyên Profile lỗi và ghi tỷ lệ thành công trên ba lượt đo. Thống kê chỉ tính các lượt thành công, không biến lỗi thành 0 ms. Mốc chỉ đạt một lượt không có độ tin cậy tương đương mốc đạt ba lượt; khi không có lượt thành công thì ghi "Không đo được". Bộ đo tiếp tục giữ cấu hình để ghi nhận tình trạng sau lỗi, vì vậy lỗi ở mốc sau có thể chịu ảnh hưởng trạng thái bộ nhớ tích lũy. Không được kết luận một ngưỡng hóa đơn cố định là giới hạn của Drill từ chuỗi thử nghiệm này.

## Các file bằng chứng

- `<mốc>/result.json`: tất cả lượt đo và tóm tắt.
- `<mốc>/*-profile.json`: Profile thật của từng lượt, giữ SQL và chi tiết operator.
- `<mốc>/*.sql`: SQL đã dùng.
- `<mốc>/*-profile-summary.png`: ảnh tóm tắt thật từ Drill Web Console.
- `<mốc>/*-profile.png`: ảnh toàn trang; tên file và query ID truy ngược được vào Profile.
- `comparison.csv`, `benchmark.html`, ba biểu đồ PNG và bản Word Benchmark: chỉ được dựng sau khi đủ cả bốn mốc.

## Giới hạn

Đây là thực nghiệm minh họa trên một máy và dữ liệu mẫu. Dữ liệu được bổ sung dần; số sản phẩm giữ ổn định, số dòng hàng/lô/sự kiện tăng. CPU của các ứng dụng local, cache và JVM có thể làm một mốc lớn chạy nhanh hơn mốc nhỏ. Ba lượt đo không đủ suy ra p95 đáng tin cậy, khả năng mở rộng cụm, hiệu năng dữ liệu GB/TB hoặc nhiều người dùng đồng thời. Không sửa số liệu để tạo đường tăng tuyến tính; diễn giải theo kết quả thật.

## Lệnh tái sử dụng

Đo đúng số hóa đơn hiện tại bằng `python database/benchmark-drill.py --orders 7000`. Công cụ dừng nếu tổng đơn không khớp; nó giữ Profile đã lưu trong thư mục mốc để tránh đo đè vô ý. Muốn một phiên thực nghiệm mới phải lưu bộ kết quả cũ riêng trước.

Chuỗi chuẩn bị dữ liệu và đo nằm ở `database/run-benchmark-stages.ps1`; script có thay đổi dữ liệu mẫu, sao lưu trước mỗi mốc và dùng tiến độ cục bộ. Không chạy lên dữ liệu cửa hàng thật. Để tái lập mốc thấp hơn, cần khôi phục đúng snapshot tương ứng ở môi trường học tập riêng; không chỉ đổi tham số số hóa đơn.

Tạo ảnh Profile bằng `node drill/capture-benchmark.mjs 7000`; cần Edge/Chrome và CDP helper của taste-skill đã cài. Sau khi đủ bốn mốc, chạy `powershell -ExecutionPolicy Bypass -File drill/render-benchmark.ps1` rồi `python docs/word/build_benchmark_report.py` để dựng biểu đồ, HTML, CSV và Word.

## Database hiện tại sau thử nghiệm

Ngày 30/10/2026 đã gỡ đợt 2.000 hóa đơn `POS-ORDERS-7000`, khôi phục dữ liệu vận hành còn 5.000 hóa đơn. Các phép đo, ảnh và Profile 7k vẫn là bằng chứng của lần thử nghiệm trước. Ba file seed trong database/ đã cập nhật về 5k; không dùng số lượng hiện tại để diễn giải lại benchmark lịch sử.
