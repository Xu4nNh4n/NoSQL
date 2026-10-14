# Kế hoạch nâng cấp đồ án Drill – Phân tích dữ liệu vận hành siêu thị

Đề tài: *Phân tích dữ liệu vận hành siêu thị đa nguồn bằng Apache Drill* (MySQL + MongoDB + Astra/Cassandra → JSON/Parquet → Drill → Spring Boot → React).

Tài liệu này dựa trên việc đọc repo `Xu4nNh4n/BigData` (README, docs, SQL Drill, một phần `DrillAnalyticsService`, cấu trúc thư mục) và rubric môn học. Các tên bảng/cột trong ví dụ SQL cần được kiểm tra lại với schema thật trước khi dùng. Các giai đoạn xếp theo thứ tự phụ thuộc, không gắn mốc tuần; mỗi giai đoạn có tiêu chí "Hoàn thành khi" để kiểm tra được.

## 1. Mục tiêu và nguyên tắc

**Mục tiêu:** chuyển đồ án từ "hệ thống bán hàng có thêm báo cáo" thành "đồ án phân tích dữ liệu lớn có bằng chứng đo được". Phần nghiệp vụ đã đủ; phần cần đầu tư là dữ liệu quy mô, độ sâu phân tích, benchmark và minh chứng.

**Nguyên tắc giữ nguyên từ tài liệu của repo (nên giữ):**

- Không backdate dữ liệu giao dịch thật, không nhân bản sự kiện để đối soát ra PASS.
- Dữ liệu mô phỏng phải nằm trong môi trường riêng và ghi rõ là mô phỏng.
- Không đưa con số hiệu năng vào báo cáo trước khi đo và lưu kết quả.
- Không thêm database/công cụ mới (Redis, Neo4j, Spark, Metabase...) ngoài phạm vi đã chốt; chiều sâu đến từ cách dùng Drill tốt hơn, không phải thêm công nghệ.

## 2. Khoảng trống hiện tại so với rubric

| Tiêu chí (trọng số) | Đã có | Còn thiếu |
| --- | --- | --- |
| Ứng dụng kỹ thuật (10%) | Bài toán siêu thị, FEFO, dòng tiền, đối soát | Diễn giải kết quả thành quyết định quản lý |
| Lưu trữ/xử lý (20%) | 3 nguồn, Outbox, JSON→Parquet có manifest | Dữ liệu chỉ 1 ngày (2026-10-08), Parquet \~233 KB; chưa có mô hình dữ liệu trình bày được, chưa đo độ trễ |
| Mô hình/công nghệ (25%) | Federated SQL, chọn nguồn theo hash SHA-256 | Chưa benchmark, chưa có Profiles, JOIN đa nguồn mới nằm trong file SQL demo |
| Trực quan hóa (25%) | Dashboard KPI 7 ngày, đối soát | MongoDB chưa hiện trên giao diện; chưa có phân tích theo nhóm hàng, top-N, hết hạn sắp tới |
| Báo cáo (10%) | README, docs tốt | Báo cáo cuối, slide, hạn chế, tài liệu tham khảo |
| Phối hợp nhóm (10%) | Không xác nhận được từ source | Repo chỉ có 1 commit; chưa có phân công và minh chứng đóng góp |

## 3. Lộ trình theo giai đoạn

### Giai đoạn 0 – Dọn nền

Mục tiêu: có nền ổn định trước khi làm thêm.

- Chốt đề bài của thầy/cô: yêu cầu tối thiểu về dữ liệu, số nguồn, có bắt buộc benchmark/demo hay không. Ghi lại thành checklist riêng.
- Xử lý lỗi Astra 401 (kiểm tra token, secure-connect bundle, keyspace). Chưa xử lý xong thì nhánh dòng tiền và đối soát không chạy được.
- Dọn repo: thêm vào `.gitignore` các thư mục `logs/`, `*.crc`, `*.tmp`, dữ liệu export; rà soát `.env`, log và lịch sử commit xem có lộ token Astra, URI MongoDB, mật khẩu MySQL không. Nếu từng commit bí mật thật, đổi (rotate) chúng.
- QA/test do bên phụ trách khác thực hiện theo yêu cầu của chủ dự án; agent triển khai không chạy test. Ghi trạng thái chờ QA, không ghi PASS nếu chưa có xác nhận.
- Từ giờ mỗi thành viên làm việc trên branch riêng và gửi Pull Request để lịch sử git phản ánh phối hợp nhóm.

**Hoàn thành khi:** backend compile, Astra được xác nhận sau khi resume, QA độc lập có báo cáo, repo không còn log runtime/credential bị track.

### Giai đoạn 1 – Dữ liệu mô phỏng quy mô

Đây là việc nền tảng: không có volume thì Parquet, benchmark và biểu đồ đều không thuyết phục.

**Cách làm:**

- Tạo môi trường riêng (schema MySQL `supermarket_sim`, thư mục export riêng), không đụng dữ liệu thật.
- Viết một bộ sinh dữ liệu có seed cố định (chạy lại cho kết quả giống nhau), đi theo đúng các quy tắc nghiệp vụ hiện có: đơn → chi tiết đơn (giá lúc bán) → thanh toán → cấp phát lô theo FEFO → hoàn/trả. Sinh qua service hoặc SQL nhưng phải giữ nhất quán giữa các bảng.
- Quy mô gợi ý (điều chỉnh theo máy và giới hạn gói Astra): 6 tháng; 200–500 SKU thuộc 8–12 nhóm; 50.000–200.000 đơn; vài trăm nghìn dòng chi tiết; số sự kiện tương ứng. Làm theo ba mức 100 nghìn / 1 triệu / 5 triệu sự kiện để vẽ được đường tăng trưởng.
- Phân phối thực tế để phân tích có chuyện để kể: cuối tuần và dịp lễ cao hơn; một vài nhóm hàng chiếm phần lớn doanh thu; \~5% đơn hủy; \~3–5% có trả hàng; kênh IN\_STORE và ONLINE khác nhau về giờ cao điểm; một số lô sắp hết hạn; một số SKU tồn thấp so với tốc độ bán.
- Cột đánh dấu nguồn (ví dụ `source = SIMULATED`) hoặc tên môi trường rõ ràng để báo cáo ghi trung thực.
- Nếu Astra gói miễn phí không chứa nổi toàn bộ sự kiện mô phỏng: ghi sự kiện mô phỏng trực tiếp ra file JSON trong môi trường riêng cho Drill đọc, và nêu rõ điều đó trong báo cáo. Đường Astra thật vẫn được chứng minh bằng dữ liệu nhỏ đã có.

**Kiểm chứng đối soát đúng cách:** tạo thêm một tập lỗi chủ động *có ghi chép* (thiếu 1 sự kiện, trùng 1 sự kiện, lệch múi giờ) để cho thấy báo cáo đối soát chuyển `PASS` thành `CHECK` và chỉ ra nguyên nhân. Đây là cách chứng minh giá trị của đối soát mà không giả PASS.

**Hoàn thành khi:** có dữ liệu nhiều tháng, đối soát PASS trên dữ liệu sạch, CHECK đúng trên các lỗi chủ động, có tài liệu mô tả cách sinh dữ liệu.

### Giai đoạn 2 – Báo cáo phân tích mới

Mục tiêu: nâng chiều sâu phân tích, đồng thời làm MongoDB hiện hữu trên giao diện.

Trước khi viết SQL, định nghĩa chỉ số và lưu thành tài liệu (và có thể thành VIEW trong Drill để mọi báo cáo dùng cùng một định nghĩa):

- *Doanh thu bán* = tổng `số lượng × đơn giá lưu trong dòng đơn` của đơn đã hoàn tất, trừ phần đã trả. Không dùng giá catalog hiện tại.
- *Tiền thu ròng* = tiền thu − tiền hoàn (đây là dòng tiền, không phải lợi nhuận).
- *Tồn khả dụng* = còn lại − giữ chỗ, chỉ lô ACTIVE và chưa hết hạn.

**Nhóm báo cáo bắt buộc (nên làm chắc):**

1. **Bán hàng theo sản phẩm/nhóm hàng (MySQL JOIN MongoDB).** Doanh thu, số lượng, tỷ trọng theo nhóm và SKU; top-N; có thuộc tính sản phẩm từ Mongo. Đây là báo cáo đa nguồn nhìn thấy được trên giao diện. Khung SQL (cần đối chiếu tên bảng/cột thật):

```sql
SELECT m.categoryName, m.sku, m.name,
       SUM(i.quantity - COALESCE(i.returned_quantity, 0)) AS qty_sold,
       SUM((i.quantity - COALESCE(i.returned_quantity, 0)) * i.unit_price) AS item_value_before_order_discount
FROM mysql_source.supermarket_api.sales_order_items i
JOIN mysql_source.supermarket_api.sales_orders o ON i.order_id = o.order_id
JOIN mysql_source.supermarket_api.products p     ON i.product_id = p.product_id
LEFT JOIN mongo_source.supermarket_catalog.products_catalog m ON p.sku_code = m.sku
WHERE ((o.order_type = 'IN_STORE' AND o.status IN ('PAID','PARTIALLY_RETURNED','RETURNED'))
    OR (o.order_type = 'ONLINE' AND o.delivery_status IN ('DELIVERED','RETURNED')
        AND o.status IN ('DELIVERED','PARTIALLY_RETURNED','RETURNED')))
  AND o.created_at >= TIMESTAMP '2026-09-01 00:00:00'
  AND o.created_at <  TIMESTAMP '2026-10-01 00:00:00'
GROUP BY m.categoryName, m.sku, m.name
ORDER BY item_value_before_order_discount DESC
```

Lưu ý: `COMPLETED` và `order_date` trong ví dụ cũ không khớp schema thật. Định nghĩa trên là giá trị món sau khi trừ số lượng trả, trước khi phân bổ `discount_amount` cấp đơn; không gọi là tiền thực thu hay lợi nhuận. Catalog có thể thiếu nên giữ `LEFT JOIN`.

2. **Hạn dùng và rủi ro hao hụt.** Lô hết hạn trong 7/14/30 ngày tới, kèm *giá trị hàng có nguy cơ* = số lượng còn lại × giá vốn lô; gom theo nhóm hàng. Giúp quản lý ưu tiên xả hàng/khuyến mãi.
3. **Gợi ý nhập hàng.** Tốc độ bán trung bình 30 ngày so với tồn khả dụng, ra "số ngày còn hàng"; SKU nào thấp hơn thời gian giao hàng của nhà cung cấp thì đề xuất nhập và số lượng.

**Nhóm báo cáo nâng cao (chọn 2–3, tùy thời gian):**

4. **Phân loại ABC (Pareto).** Dùng window function (`SUM() OVER`) để tính doanh thu tích lũy; nhóm A chiếm \~80% doanh thu. Biểu đồ Pareto rất trực quan.
5. **Phân tích giỏ hàng.** Cặp sản phẩm hay được mua cùng nhau (self-join chi tiết đơn), kèm support/lift. Đây cũng là truy vấn nặng, rất hợp để đưa vào benchmark.
6. **Phân tích theo thời gian và kênh.** Doanh thu theo giờ × thứ trong tuần, IN\_STORE vs ONLINE, COD vs chuyển khoản.
7. **Tỷ lệ trả/hoàn theo sản phẩm hoặc nhóm.** JOIN bảng trả hàng với chi tiết đơn và catalog; chỉ ra nhóm hàng có vấn đề chất lượng.

**Báo cáo chất lượng dữ liệu (rẻ nhưng ghi điểm):** ngày thiếu bản export, số sự kiện đang chờ Outbox, SKU có ở MySQL nhưng thiếu ở Mongo (và ngược lại), kiểu khóa lệch giữa các nguồn, độ trễ đồng bộ trung bình.

**Hoàn thành khi:** ít nhất 3 báo cáo bắt buộc + 2 báo cáo nâng cao chạy được qua Drill REST, mỗi báo cáo có định nghĩa chỉ số, SQL, và một câu "quản lý quyết định gì từ kết quả này".

### Giai đoạn 3 – Kỹ thuật Parquet và benchmark (làm song song với giai đoạn 2)

Đây là phần ăn điểm nhất ở tiêu chí công nghệ (25%).

**Bố cục lưu trữ cần so sánh:**

- A: JSON theo ngày (hiện trạng).
- B: Parquet theo ngày (hiện trạng, đã có CTAS/Snappy).
- C: Parquet gộp theo tháng, phân vùng theo thư mục/cột ngày để Drill cắt bớt phân vùng (partition pruning).
- D (đối chứng): chạy trực tiếp trên MySQL.

**Bộ truy vấn chuẩn (giữ cố định cho mọi lần đo):**

- Q1 tổng hợp dòng tiền theo ngày; Q2 lọc 7 ngày; Q3 gom theo sản phẩm; Q4 JOIN MySQL–Mongo; Q5 JOIN ba nguồn; Q6 giỏ hàng (truy vấn nặng).

**Quy trình đo:**

- Mỗi truy vấn chạy 5 lần, ghi trung vị; tách lần chạy nguội (cold) và nóng (warm); ghi cấu hình máy (CPU, RAM, heap của Drill).
- Ghi lại: thời gian, số dòng quét, dung lượng đọc, bộ nhớ đỉnh. Lưu ảnh Drill Profile (Web Console cổng 8047) cho từng truy vấn.
- Dùng `EXPLAIN PLAN FOR` để chứng minh: bộ lọc/gộp được đẩy xuống MySQL (pushdown) hay Drill tự xử lý; phân vùng nào được cắt bỏ.
- Đo ở ba mức dữ liệu (100 nghìn / 1 triệu / 5 triệu sự kiện) để thấy xu hướng.
- Chỉ viết con số vào báo cáo sau khi đã đo; ghi rõ điều kiện đo và hạn chế.

**Cải tiến kèm theo:**

- Xuất tăng dần theo ngày thay vì xuất lại toàn bộ; tự động hóa bước chuyển Parquet bằng job định kỳ.
- Chính sách giữ/xóa các bảng Parquet phiên bản cũ để không phình dung lượng.
- Tạo VIEW trong Drill cho các chỉ số dùng chung.

**Hoàn thành khi:** có bảng kết quả (bố cục × truy vấn × quy mô), ảnh Profile, kết luận bằng lời (khi nào Parquet có lợi, khi nào không, vì sao) và ghi rõ hạn chế của phép đo.

### Giai đoạn 4 – Giao diện và trực quan hóa

- Khu vực "Phân tích vận hành" với các tab theo câu hỏi quản lý; mỗi tab: một câu mô tả mục đích, bộ lọc, chỉ số chính, bảng chi tiết.
- Biểu đồ phù hợp từng loại: cột top-N, Pareto, heatmap giờ × thứ, cơ cấu theo nhóm hàng, dòng thời gian hạn dùng.
- Mỗi báo cáo có thanh trạng thái dữ liệu (nguồn JSON/PARQUET, thời điểm xuất, ngày còn thiếu) và phần mở rộng "Nguồn dữ liệu và SQL" (SQL, query ID, thời gian chạy).
- Trạng thái rỗng/lỗi rõ ràng ("Chưa có bản xuất ngày này"), không hiển thị số 0 gây hiểu nhầm.
- Xuất CSV cho bảng chi tiết.
- Một trang "Kiến trúc và luồng dữ liệu" hoặc ảnh sơ đồ lineage dùng trong demo.

**Hoàn thành khi:** quay được video demo 5–7 phút đi trọn luồng và mọi số liệu trên màn hình khớp với kết quả SQL chạy trực tiếp trên Drill.

### Giai đoạn 5 – Báo cáo, slide, nhóm

**Báo cáo (gợi ý mục lục):** bối cảnh và bài toán; kiến trúc và vai trò từng nguồn; mô hình dữ liệu; quy trình sinh dữ liệu mô phỏng; định nghĩa chỉ số; các báo cáo và kết quả; benchmark và phân tích; đối soát và chất lượng dữ liệu; hạn chế; hướng phát triển; tài liệu tham khảo (tài liệu Apache Drill về JOIN, Parquet, chọn storage format).

**Slide (12–15 trang):** vấn đề → kiến trúc → vai trò từng nguồn → dữ liệu và quy mô → 3–4 báo cáo tiêu biểu → benchmark → đối soát → demo → hạn chế → phân công.

**Câu hỏi hội đồng nên chuẩn bị trả lời:**

- Vì sao dùng Drill mà không viết thẳng SQL trên MySQL?
- Vì sao Cassandra không được Drill đọc trực tiếp, mà qua JSON/Parquet?
- Parquet nhanh hơn bao nhiêu, đo thế nào, trong điều kiện nào?
- Dữ liệu có thật không? Mô phỏng thế nào?
- Outbox giải quyết vấn đề gì? Độ trễ đồng bộ bao nhiêu?
- Sai lệch đối soát xuất phát từ đâu và xử lý ra sao?

**Phân công nhóm (mẫu, điền tên thật):**

| Vai trò | Việc chính | Minh chứng |
| --- | --- | --- |
| Backend/dữ liệu | Sinh dữ liệu mô phỏng, Outbox, đồng bộ, Astra | PR, tài liệu sinh dữ liệu |
| Drill/benchmark | SQL, Parquet, đo hiệu năng, Profiles | Bảng kết quả, ảnh Profile |
| Frontend | Tab phân tích, biểu đồ, trạng thái dữ liệu | PR, video demo |
| QA/đối soát | Chạy test, ca lỗi chủ động, kiểm số liệu | Báo cáo kiểm thử |
| Báo cáo/slide | Viết báo cáo, slide, kịch bản demo | Bản nháp, lịch sử chỉnh sửa |

Ghi lại ai demo phần nào; giữ ảnh họp, issue/board, lịch sử PR làm minh chứng cho tiêu chí 10% về phối hợp.

## 4. Ma trận rubric → sản phẩm cần có

| Tiêu chí | Sản phẩm/minh chứng |
| --- | --- |
| Ứng dụng kỹ thuật (10%) | Mỗi báo cáo kèm "quyết định quản lý rút ra"; ví dụ cụ thể về phát hiện thiếu hàng/lệch tiền |
| Lưu trữ/xử lý (20%) | Dữ liệu nhiều tháng; sơ đồ mô hình dữ liệu; báo cáo chất lượng dữ liệu; số liệu độ trễ đồng bộ |
| Mô hình/công nghệ (25%) | JOIN đa nguồn chạy thật; bảng benchmark; ảnh Profile; phân tích EXPLAIN/pushdown/partition pruning |
| Trực quan hóa (25%) | Tab phân tích với biểu đồ phù hợp; video demo; số liệu khớp SQL |
| Báo cáo (10%) | Báo cáo cuối + slide + hạn chế + tài liệu tham khảo |
| Phối hợp nhóm (10%) | Bảng phân công; lịch sử PR/issue; người demo từng phần |

## 5. Thứ tự thực hiện

1. Giai đoạn 0 (dọn nền): điều kiện cho mọi giai đoạn sau.
2. Giai đoạn 1 (dữ liệu mô phỏng): xong trước khi đo benchmark và dựng biểu đồ.
3. Giai đoạn 2 (báo cáo mới) và giai đoạn 3 (Parquet, benchmark): làm song song khi đã có dữ liệu; benchmark dùng chính các truy vấn của giai đoạn 2.
4. Giai đoạn 4 (giao diện): sau khi các báo cáo chạy ổn qua Drill.
5. Giai đoạn 5 (báo cáo, slide, nhóm): chốt sau cùng, dựa trên số liệu và ảnh đã thu.

Nếu thời gian ngắn hơn, cắt theo thứ tự: báo cáo nâng cao (6, 7) → giỏ hàng (5) → mức dữ liệu 5 triệu. Không cắt: dữ liệu nhiều tháng, báo cáo bán hàng JOIN Mongo, benchmark JSON vs Parquet, minh chứng nhóm.

## 6. Rủi ro và cách giảm

| Rủi ro | Cách giảm |
| --- | --- |
| Gói Astra miễn phí giới hạn dung lượng/thao tác | Kiểm tra giới hạn sớm; dữ liệu mô phỏng lớn ghi ra JSON trong môi trường riêng và nói rõ trong báo cáo |
| Dữ liệu mô phỏng bị nghi "làm giả" | Ghi rõ nguồn mô phỏng, seed cố định, tài liệu hóa phân phối; không trộn vào dữ liệu thật |
| Drill hết bộ nhớ ở 5 triệu dòng | Tăng heap, đo từng mức, nếu không chạy nổi thì ghi nhận như một kết quả và hạn chế |
| Benchmark cho kết quả không như kỳ vọng (Parquet không nhanh hơn) | Vẫn là kết quả hợp lệ; phân tích nguyên nhân (dữ liệu nhỏ, định dạng, cache) thay vì chỉnh số |
| Sai tên bảng/cột khi viết SQL mới | Đọc schema Liquibase trong `db/changelog` trước; chạy thử trên Drill Web Console |
| Lộ thông tin nhạy cảm trong repo | Rà `.env`, log, lịch sử commit; rotate token nếu cần |
| Trễ phối hợp nhóm | Chốt phân công và hạn từng việc ngay từ đầu; mỗi người làm trên branch + PR |

## 7. Checklist minh chứng cuối

- Đề bài/yêu cầu của môn đã đối chiếu từng mục.
- Dữ liệu mô phỏng nhiều tháng + tài liệu sinh dữ liệu.
- Ít nhất 3 báo cáo bắt buộc và 2 báo cáo nâng cao chạy qua Drill, có định nghĩa chỉ số.
- Bảng benchmark, ảnh Drill Profile, kết luận và hạn chế.
- Kết quả đối soát PASS (dữ liệu sạch) và CHECK (ca lỗi chủ động) kèm phân tích nguyên nhân.
- Video demo + ảnh chụp giao diện.
- Báo cáo cuối, slide, tài liệu tham khảo.
- Bảng phân công, lịch sử PR/issue, người demo từng phần.
- Kết quả chạy test và repo đã dọn sạch.

## 8. Prompt giao cho AI (sao chép và dán)

Dán phần dưới vào Claude Code hoặc AI agent khác, chạy ở thư mục gốc repo, và đính kèm tài liệu kế hoạch này (lưu thành `docs/KE_HOACH_NANG_CAP.md`). Prompt buộc AI tự tìm hiểu, tự làm, tự kiểm tra và sửa, nhưng cấm bịa số liệu.

```markdown
Bạn là kỹ sư dữ liệu/phần mềm đang nâng cấp đồ án môn Big Data trong repo này. Làm việc tự chủ theo các bước dưới đây; chỉ dừng hỏi người dùng khi gặp chặn thật sự (thiếu thông tin đăng nhập, quyết định ảnh hưởng đến kết quả nộp).

# Bối cảnh
- Đề tài: Phân tích dữ liệu vận hành siêu thị đa nguồn bằng Apache Drill. MySQL (đơn, chi tiết đơn, thanh toán, lô, tồn), MongoDB (catalog), Astra/Cassandra (sự kiện, xuất ra JSON rồi chuyển Parquet cho Drill đọc). Backend Spring Boot, frontend React.
- Kế hoạch: docs/KE_HOACH_NANG_CAP.md (đã đính kèm). Rubric chấm: ứng dụng kỹ thuật 10%, lưu trữ/xử lý 20%, mô hình/công nghệ 25%, trực quan hóa 25%, báo cáo 10%, phối hợp nhóm 10%.

# Quy tắc cứng
1. Không bịa số liệu. Mọi con số hiệu năng phải do bạn chạy đo thật; lưu đầu ra thô vào docs/benchmark/. Việc nào không chạy được thì ghi "chưa chạy" kèm lý do.
2. Không backdate hoặc nhân bản dữ liệu thật. Dữ liệu mô phỏng nằm ở môi trường riêng (schema/thư mục riêng), gắn nhãn SIMULATED. Đối soát phải PASS nhờ dữ liệu nhất quán, không ép PASS.
3. Không commit bí mật (.env, token Astra, mật khẩu). Không xóa/sửa dữ liệu thật, không chạy lệnh phá hủy.
4. Không thêm database/công nghệ ngoài phạm vi (Redis, Neo4j, Spark, Metabase...).
5. Một nhánh git cho mỗi giai đoạn, commit nhỏ, thông điệp rõ; không viết lại lịch sử.
6. Trước khi viết SQL mới, đọc schema thật (Liquibase, entity, seed Mongo/Cassandra); không đoán tên bảng/cột.

# Quy trình
## Bước A – Tìm hiểu (chỉ đọc)
Đọc README, docs/, db changelog, entity, drill/*.sql, DrillAnalyticsService, các controller phân tích, script chuyển Parquet, phần báo cáo ở frontend. Ghi docs/ai/01_khao_sat.md: sơ đồ nguồn dữ liệu, danh sách API và SQL hiện có, cách Drill được cấu hình, cái gì chạy được/không chạy được, rủi ro, chỗ tài liệu lệch với code.

## Bước B – Lập kế hoạch thực thi
Từ khảo sát và kế hoạch nâng cấp, ghi docs/ai/02_ke_hoach_thuc_thi.md: việc theo giai đoạn 0–5, thứ tự phụ thuộc, tiêu chí hoàn thành đo được cho từng việc. Ghi xong thì tiếp tục làm, không cần chờ xác nhận.

## Bước C – Thực hiện theo giai đoạn
- Giai đoạn 0: kiểm tra trạng thái Astra (401/hibernation), dọn .gitignore và log, rà bí mật trong file và lịch sử commit; chuyển test cho QA được phân công, không chạy từ agent.
- Giai đoạn 1: bộ sinh dữ liệu mô phỏng có seed cố định, nhiều tháng, 3 mức quy mô (100k/1M/5M sự kiện), phân phối thực tế; kèm tập lỗi chủ động có ghi chép để chứng minh đối soát phát hiện được sai lệch.
- Giai đoạn 2: báo cáo bán hàng theo nhóm hàng (MySQL JOIN MongoDB), hạn dùng 7/14/30 ngày, gợi ý nhập hàng; sau đó 2 báo cáo nâng cao (ABC/Pareto, giỏ hàng, theo giờ–kênh hoặc tỷ lệ trả). Định nghĩa chỉ số trong tài liệu và VIEW Drill.
- Giai đoạn 3: so sánh JSON theo ngày, Parquet theo ngày, Parquet gộp theo tháng có phân vùng, và MySQL trực tiếp trên bộ truy vấn Q1–Q6; mỗi truy vấn chạy 5 lần, tách nguội/nóng; lưu EXPLAIN và Profile; viết kết luận kèm hạn chế.
- Giai đoạn 4: giao diện "Phân tích vận hành" có tab, biểu đồ phù hợp, thanh trạng thái dữ liệu, phần "Nguồn dữ liệu và SQL", trạng thái rỗng/lỗi rõ ràng, xuất CSV.
- Giai đoạn 5: khung báo cáo, slide, kịch bản demo, bộ câu hỏi bảo vệ, tất cả dựa trên kết quả thật.

## Bước D – Tự kiểm tra và sửa
Sau mỗi giai đoạn: compile/build; test, gọi API với dữ liệu, so sánh UI–SQL và benchmark do QA/người phụ trách được phân công thực hiện. Agent không chạy test theo yêu cầu của chủ dự án. Ghi docs/ai/NHAT_KY.md: đã làm gì, lệnh đã chạy, kết quả, việc còn lại; không gọi build là test.

# Việc phải trả lại cho người
Thông tin đăng nhập và quota Astra; xác nhận đề bài của giảng viên; bảng phân công và minh chứng làm nhóm (PR, ảnh họp); quay video demo; quyết định cuối về nội dung nộp. Liệt kê rõ các mục này ở cuối báo cáo.

# Báo cáo cuối
Tạo docs/ai/03_bao_cao_ket_qua.md: việc đã hoàn thành (kèm đường dẫn bằng chứng), việc đã chạy nhưng chưa kiểm chứng, việc chưa làm và lý do, số liệu benchmark (lấy từ file thô), rủi ro còn lại.
```
