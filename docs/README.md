# Tài liệu đề tài Apache Drill

Phạm vi hiện tại: MySQL giao dịch, MongoDB Atlas catalog, Astra/Cassandra sự kiện, JSON/Parquet và Apache Drill. Redis, Neo4j, tích điểm và phân tích click/funnel không nằm trong phạm vi đã chốt.

| Tài liệu | Nội dung |
|---|---|
| [README chạy dự án](../README.md) | Cấu hình, luồng dữ liệu, API phân tích và chuyển Parquet |
| [Thực trạng và việc tiếp theo](KE_HOACH_TRIEN_KHAI_BIGDATA.md) | Phần đã triển khai, giới hạn và thứ tự hoàn thiện |
| [Kế hoạch nâng cấp Drill](KE_HOACH_NANG_CAP.md) | Giai đoạn nâng cấp theo rubric, dữ liệu, báo cáo và benchmark |
| [Khảo sát nâng cấp](ai/01_khao_sat.md) | Vai trò từng nguồn, schema thật, sai khác trong ví dụ SQL cũ |
| [Trạng thái triển khai](ai/02_ke_hoach_thuc_thi.md) | Việc đã làm, phần chờ dữ liệu/QA và điều kiện hoàn thành |
| [Nhật ký triển khai](ai/NHAT_KY.md) | Thay đổi, lệnh build và việc còn lại |
| [Báo cáo kết quả](ai/03_bao_cao_ket_qua.md) | Bản khung trung thực; benchmark và Astra đang chờ xác nhận |
| [Sinh dữ liệu mô phỏng](../database/simulation/README.md) | Dataset riêng, cách nạp và giới hạn |
| [Đối chiếu rubric](01_lo_trinh_va_kien_truc/04_KE_HOACH_BIG_DATA_APACHE_DRILL.md) | Sáu tiêu chí và minh chứng cần chuẩn bị |
| [Demo và bàn giao](01_lo_trinh_va_kien_truc/05_THUC_THI_BIG_DATA_APACHE_DRILL.md) | Các thao tác demo, nội dung bàn giao bên kiểm thử và mẫu phân công |
| [Phân vai lưu trữ](NOSQL_BIGDATA_PROJECT_PLAN.txt) | Quyền sở hữu dữ liệu của ba database |

Các mục ghi đã triển khai mô tả source hiện tại, không khẳng định đã được kiểm thử toàn bộ. Không chạy test theo yêu cầu chủ dự án; benchmark và kiểm thử do bên phụ trách riêng thực hiện.
