"""Nội dung SRS v2.0. Mỗi phần tử: (loại, ...). Loại: h1, h2, h3, p, b, code, table, kv, img, pb."""

C = []


def h1(t): C.append(("h1", t))
def h2(t): C.append(("h2", t))
def h3(t): C.append(("h3", t))
def p(t): C.append(("p", t))
def b(*items):
    for t in items:
        C.append(("b", t))
def code(t): C.append(("code", t))
def table(headers, rows, widths): C.append(("table", headers, rows, widths))
def kv(pairs): C.append(("kv", pairs))
def img(name, caption, w=16): C.append(("img", name, caption, w))


# ---------------------------------------------------------------- 1
h1("1. GIỚI THIỆU")
h2("1.1 Mục đích tài liệu và đối tượng đọc")
p("Tài liệu này đặc tả yêu cầu phần mềm cho hệ thống quản lý bán hàng và tồn kho của một siêu thị bán trực tiếp tại quầy. "
  "Hệ thống là đồ án cuối kỳ học phần Dữ liệu NoSQL, lưu dữ liệu trên 4 loại cơ sở dữ liệu NoSQL: MongoDB (Document), "
  "Cassandra (Column-family), Neo4j (Graph) và Redis (Key-Value). Tài liệu theo khung IEEE 830 / ISO/IEC/IEEE 29148, "
  "có bổ sung yêu cầu dữ liệu và mẫu truy cập dữ liệu (access pattern) dành riêng cho NoSQL.")
p("Đối tượng đọc:")
b("**Giảng viên** hướng dẫn và chấm điểm: đối chiếu phạm vi, chức năng và lý do chọn từng loại CSDL.",
  "**Nhóm phát triển**: căn cứ để thiết kế CSDL và hệ thống (SDS, Chương 4 của báo cáo) và lập trình.",
  "**Người kiểm thử**: căn cứ để viết ca kiểm thử theo ma trận truy vết ở mục 8.")

h2("1.2 Phạm vi sản phẩm")
p("Hệ thống gồm backend Spring Boot cung cấp REST API xác thực bằng JWT, giao diện web React dùng nội bộ tại cửa hàng, "
  "và 4 CSDL NoSQL chạy trên dịch vụ cloud. Hệ thống phục vụ đủ một vòng nghiệp vụ của cửa hàng: "
  "**nhập hàng → bán hàng → trả hàng → chốt ca → thu hồi lô lỗi**.")
table(["Trong phạm vi", "Ngoài phạm vi"], [
    ("Tài khoản, phân quyền 3 vai trò, phiên đăng nhập, lịch sử đăng nhập",
     "Bán hàng online: giỏ hàng khách, giao hàng, thu tiền khi nhận hàng"),
    ("Danh mục: sản phẩm (thuộc tính linh hoạt), nhóm hàng, nhà cung cấp",
     "Hồ sơ khách hàng, tích điểm thành viên, ví điện tử, cổng thanh toán tự động"),
    ("Kho theo lô: phiếu nhập, nhận lô, cấp phát FEFO, điều chỉnh giảm, thẻ kho",
     "Dashboard phân tích dữ liệu lớn (Apache Drill, Parquet) của bản BigData trước"),
    ("Bán tại quầy có khuyến mãi 4 loại, đơn treo, gợi ý mua kèm",
     "Giới hạn số suất khuyến mãi, mã giảm giá cho từng khách"),
    ("Trả hàng kèm hoàn tiền, chốt ca thu ngân", "Nhiều chi nhánh, chuyển kho giữa chi nhánh"),
    ("Truy vết và thu hồi lô, thống kê bán hàng và top bán chạy, xuất CSV",
     "Thông báo đẩy realtime; sao lưu bằng giao diện (sao lưu làm bằng script); ứng dụng di động"),
], [50, 50])

h2("1.3 Định nghĩa, từ viết tắt và thuật ngữ")
table(["Thuật ngữ", "Tiếng Anh", "Giải thích"], [
    ("SRS / SDS", "Software Requirements / Design Specification", "Tài liệu đặc tả yêu cầu / thiết kế phần mềm."),
    ("FR / NFR", "Functional / Non-Functional Requirement", "Yêu cầu chức năng / phi chức năng."),
    ("AP", "Access Pattern", "Mẫu truy cập dữ liệu: một câu hỏi cụ thể ứng dụng gửi tới CSDL, là đầu vào để thiết kế NoSQL."),
    ("FEFO", "First Expired, First Out", "Lô có hạn dùng sớm nhất được xuất trước."),
    ("SKU", "Stock Keeping Unit", "Mã sản phẩm duy nhất do cửa hàng đặt."),
    ("Lô hàng", "Batch", "Một lần nhập của một sản phẩm, có số lượng, giá vốn, ngày sản xuất, hạn dùng và tồn còn lại riêng."),
    ("Phiếu nhập", "Purchase order", "Chứng từ đặt hàng nhà cung cấp; khi nhận hàng sinh ra các lô."),
    ("Cấp phát lô", "Batch allocation", "Bản ghi cho biết một dòng đơn lấy bao nhiêu hàng từ lô nào."),
    ("Thẻ kho", "Stock card / movement", "Sổ ghi mọi biến động tồn kho của một sản phẩm, chỉ thêm, không sửa."),
    ("Đơn treo", "Held cart", "Giỏ hàng thu ngân tạm giữ để phục vụ khách khác, tự hết hạn sau 30 phút."),
    ("Chốt ca", "Shift closing", "Đối chiếu tổng tiền hệ thống ghi nhận với tiền thực có trong két cuối ca."),
    ("Khuyến mãi", "Promotion", "Quy tắc giảm giá: giảm %, giảm tiền, mua X tặng Y, giảm theo tổng đơn."),
    ("Tính lặp an toàn", "Idempotency", "Gửi lặp một yêu cầu cho kết quả như gửi một lần."),
    ("Xóa mềm", "Soft delete", "Đánh dấu không hoạt động thay vì xóa hẳn, để giữ liên kết lịch sử."),
    ("Document / Collection", "—", "Đơn vị lưu trữ JSON/BSON của MongoDB / nhóm các document."),
    ("Partition key / Clustering key", "—", "Khóa phân vùng và khóa sắp xếp trong một bảng Cassandra."),
    ("Node / Relationship", "—", "Đỉnh và cạnh có thuộc tính trong Neo4j; truy vấn bằng Cypher."),
    ("TTL", "Time To Live", "Thời gian sống của dữ liệu; hết hạn thì CSDL tự xóa (Redis, Cassandra)."),
    ("Sorted Set, Pub/Sub", "—", "Kiểu tập có điểm số và cơ chế phát/nhận tin của Redis."),
    ("Nguồn sự thật", "Source of truth", "CSDL giữ dữ liệu gốc dùng để ra quyết định; ở đây là MongoDB."),
    ("JWT", "JSON Web Token", "Chuỗi xác thực không giữ phiên, gửi trong header Authorization."),
], [20, 26, 54])

h2("1.4 Tài liệu tham chiếu")
b("IEEE Std 830-1998 và ISO/IEC/IEEE 29148:2018 – Requirements engineering.",
  "Mẫu báo cáo đồ án CSDL NoSQL (SRS + SDS) của học phần – file REFS_NoSQL_SRS&SDS_Template.docx.",
  "Bảng báo cáo minh chứng và tự đánh giá đồ án – file BaoCaoMinhChung_DoAn_DuLieuNoSQL.docx.",
  "Tài liệu thiết kế nội bộ: docs/superpowers/specs/2026-11-07-nosql-redesign-design.md.",
  "Tài liệu chính thức MongoDB, DataStax Astra DB / Apache Cassandra, Neo4j, Redis (phiên bản ở mục 7.3).",
  "SRS phiên bản 1.1 (16/10/2026) – bản trước khi chuyển sang đồ án NoSQL.")

h2("1.5 Tổng quan cấu trúc tài liệu và lịch sử thay đổi")
p("Phần 2 mô tả tổng quan sản phẩm, tác nhân, ràng buộc. Phần 3 đặc tả 14 yêu cầu chức năng kèm các sơ đồ use case, "
  "BPMN, activity, sequence, state machine và DFD. Phần 4 nêu yêu cầu phi chức năng đo được. Phần 5 mô tả yêu cầu dữ liệu "
  "(3V, mô hình khái niệm, từ điển dữ liệu). Phần 6 là bảng mẫu truy cập dữ liệu. Phần 7 nêu yêu cầu giao diện. "
  "Phần 8 là ma trận truy vết. Phần 9 gồm giả định, ràng buộc, rủi ro và phụ lục.")
table(["Phiên bản", "Ngày", "Nội dung thay đổi"], [
    ("1.1", "16/10/2026", "SRS gốc: 14 FR, 4 vai trò, khuyến mãi ngoài phạm vi."),
    ("2.0", "07/11/2026", "Viết lại theo mẫu NoSQL: 3 vai trò (bỏ MANAGER); 15 FR, thêm khuyến mãi 4 loại, đơn treo, chốt ca, "
                          "truy vết và thu hồi lô, gợi ý mua kèm, thống kê và top bán chạy, lịch sử đăng nhập; phiếu nhập; "
                          "trả hàng một bước; thêm sơ đồ, đặc tính 3V, bảng access pattern, ma trận truy vết FR → AP → CSDL → TC. "
                          "Các FR nhỏ được gộp theo use case (hồ sơ và lịch sử đăng nhập vào FR-01; nhóm hàng, nhà cung cấp, "
                          "tra cứu vào FR-03; điều chỉnh tồn vào FR-04; FEFO vào FR-06; top bán chạy vào FR-14)."),
    ("2.1", "07/11/2026", "Bỏ quản lý khách hàng (FR-03 cũ): đơn bán lẻ không gắn khách; truy vết lô trả danh sách đơn đã bán. "
                          "Còn 14 FR, đánh số lại FR-03 → FR-14 và TC tương ứng; bỏ thực thể Khách hàng và node :Customer."),
], [14, 16, 70])

# ---------------------------------------------------------------- 2
h1("2. MÔ TẢ TỔNG QUAN")
h2("2.1 Bối cảnh sản phẩm và sơ đồ ngữ cảnh")
p("Hệ thống phục vụ một siêu thị bán trực tiếp tại quầy. Ba nhóm người dùng nội bộ thao tác qua trình duyệt; "
  "hệ thống không tích hợp hệ thống bên ngoài nào ngoài 4 CSDL NoSQL chạy trên cloud. Khách hàng không đăng nhập "
  "và không có hồ sơ: mọi đơn là đơn bán lẻ, chỉ ghi thu ngân và hình thức thanh toán.")
img("01_so_do_ngu_canh.png", "Sơ đồ ngữ cảnh của hệ thống")

h2("2.2 Quy trình nghiệp vụ")
p("Điểm cốt lõi của nghiệp vụ là tồn kho theo lô: mỗi sản phẩm có nhiều lô với hạn dùng khác nhau và hàng luôn được "
  "bán theo lô hết hạn trước. Thủ kho lập phiếu nhập gửi nhà cung cấp; khi hàng về, thủ kho nhập số lượng thực nhận, "
  "ngày sản xuất, hạn dùng và hệ thống sinh lô. Thu ngân bán tại quầy, hệ thống tự áp khuyến mãi, chọn lô theo FEFO, "
  "trừ kho và ghi nhận thanh toán. Khách trả hàng thì thu ngân lập phiếu trả và hoàn tiền ngay; hàng còn bán được quay về "
  "đúng lô đã xuất. Cuối ca, thu ngân chốt ca bằng cách đối chiếu tổng thu với tiền trong két. Khi nhà cung cấp báo lô lỗi, "
  "quản lý truy vết lô để biết lô đã bán ra trong những đơn nào và thu hồi phần còn tồn. Mọi thay đổi tồn kho đều để lại bản ghi trên thẻ kho.")

h2("2.3 Tóm tắt chức năng sản phẩm")
table(["Phân hệ", "Yêu cầu chức năng"], [
    ("Tài khoản", "FR-01 Tài khoản và phiên đăng nhập (đăng nhập, làm mới phiên, hồ sơ, đổi mật khẩu, lịch sử đăng nhập) · "
                  "FR-02 Người dùng"),
    ("Danh mục và kho", "FR-03 Danh mục hàng hóa (sản phẩm, nhóm hàng, nhà cung cấp, tra cứu, cảnh báo) · "
                        "FR-04 Nhập hàng và điều chỉnh tồn · FR-05 Thẻ kho"),
    ("Bán hàng và trả hàng", "FR-06 Bán tại quầy (cấp phát FEFO) · FR-07 Đơn treo · FR-08 Trả hàng, hoàn tiền · "
                             "FR-09 Tra cứu đơn, phiếu trả, thanh toán · FR-10 Khuyến mãi 4 loại"),
    ("Ca làm việc, truy vết và thống kê", "FR-11 Chốt ca · FR-12 Truy vết, thu hồi lô · FR-13 Gợi ý mua kèm · "
                                          "FR-14 Thống kê, top bán chạy, xuất CSV"),
], [28, 72])

h2("2.4 Tác nhân và đặc điểm người dùng")
table(["Vai trò", "Mô tả", "Đặc điểm / kỹ năng"], [
    ("ADMIN", "Chủ cửa hàng kiêm quản lý: người dùng, nhóm hàng, sản phẩm, nhà cung cấp, khuyến mãi, thu hồi lô, "
              "thống kê; có toàn quyền của STAFF và WAREHOUSE.", "Am hiểu nghiệp vụ; ra quyết định giá và khuyến mãi."),
    ("WAREHOUSE", "Thủ kho: phiếu nhập, nhận lô, điều chỉnh giảm tồn, sản phẩm, nhà cung cấp, thẻ kho, cảnh báo.",
     "Nhập liệu thường xuyên, cần số tồn chính xác."),
    ("STAFF", "Thu ngân: bán tại quầy, đơn treo, trả hàng và hoàn tiền, chốt ca của mình.",
     "Thao tác nhanh tại quầy; ít kiến thức kỹ thuật."),
], [16, 54, 30])
p("So với bản 1.1, vai trò MANAGER được gộp vào ADMIN vì cửa hàng nhỏ không tách người quản lý với chủ cửa hàng; "
  "giảm số vai trò giúp phân quyền và demo rõ ràng hơn.")

h2("2.5 Môi trường vận hành")
p("Backend Java 21, Spring Boot 4.1, chạy trên JVM. Giao diện React + TypeScript (Vite) trên trình duyệt hiện đại. "
  "CSDL dùng dịch vụ cloud: MongoDB Atlas, DataStax Astra DB (Cassandra), Neo4j AuraDB, Redis Cloud (hoặc Upstash). "
  "Không dùng Docker; mọi kết nối tới CSDL qua Internet có mã hóa TLS.")

h2("2.6 Ràng buộc thiết kế và triển khai")
b("Học phần NoSQL: dữ liệu phải nằm trên đủ 4 loại CSDL NoSQL; không dùng RDBMS.",
  "Ngân sách hạ tầng 0 đồng: chỉ dùng gói miễn phí (Atlas M0, Astra Free, Aura Free, Redis Cloud 30 MB). "
  "Số node do nhà cung cấp quản lý; Aura Free giới hạn khoảng 200 nghìn node và 400 nghìn quan hệ.",
  "Ngôn ngữ bắt buộc: Java (Spring Boot) cho backend; giao diện tiếng Việt.",
  "Không có giao dịch xuyên 4 CSDL. Tính đúng của tiền, tồn kho và trạng thái đơn dựa vào một nguồn sự thật là MongoDB; "
  "Cassandra và Neo4j được ghi sau, Redis chỉ giữ dữ liệu tạm.",
  "Mọi thay đổi tồn kho phải đi qua lô hàng; không sửa tồn trực tiếp trên sản phẩm.",
  "Xác thực JWT không giữ phiên; mật khẩu băm BCrypt, tối đa 72 byte.")

h2("2.7 Giả định và phụ thuộc")
b("Xác nhận chuyển khoản tại quầy do thu ngân thực hiện bằng cách nhập mã giao dịch sau khi kiểm tra tiền về.",
  "Hoàn tiền thực hiện tại quầy, ghi nhận cùng lúc với phiếu trả.",
  "Hạn dùng, ngày sản xuất là ngày lịch; múi giờ thống nhất Asia/Ho_Chi_Minh cho khái niệm \"hôm nay\".",
  "Tiền tệ VND, không có phần lẻ.",
  "Phụ thuộc: kết nối Internet ổn định tới 4 dịch vụ cloud; MongoDB Atlas là replica set nên hỗ trợ transaction nhiều document.",
  "Gói Redis miễn phí có thể không lưu bền; mất dữ liệu Redis chỉ buộc người dùng đăng nhập lại, không mất đơn hay tồn.")

# ---------------------------------------------------------------- 3
h1("3. YÊU CẦU CHỨC NĂNG")
h2("3.1 Danh sách yêu cầu chức năng")
FR = [
    ("FR-01", "Tài khoản và phiên đăng nhập", "Tất cả", "Đăng nhập, refresh token xoay vòng, đăng xuất; hồ sơ, đổi mật khẩu; "
     "lịch sử đăng nhập 90 ngày", "Cao", "AP-01, AP-02, AP-16"),
    ("FR-02", "Quản lý người dùng", "ADMIN", "Thêm, sửa, khóa tài khoản; gán vai trò", "Cao", "AP-01"),
    ("FR-03", "Danh mục hàng hóa", "Tất cả xem; ADMIN, WAREHOUSE ghi", "Sản phẩm (thuộc tính linh hoạt), nhóm hàng, nhà cung cấp; "
     "tra cứu, cảnh báo sắp hết hàng và lô hết hạn", "Cao", "AP-03 → AP-08"),
    ("FR-04", "Nhập hàng và điều chỉnh tồn", "ADMIN, WAREHOUSE", "Phiếu nhập DRAFT → RECEIVED sinh lô; nhập lô lẻ; "
     "điều chỉnh giảm tồn theo lô", "Cao", "AP-08, AP-22, AP-24"),
    ("FR-05", "Thẻ kho", "Tất cả", "Lịch sử biến động theo sản phẩm, theo tháng", "Cao", "AP-13"),
    ("FR-06", "Bán tại quầy", "ADMIN, STAFF", "Tính khuyến mãi, cấp phát FEFO, thu tiền, in hóa đơn", "Cao",
     "AP-03, AP-07, AP-09, AP-17, AP-19"),
    ("FR-07", "Đơn treo tại quầy", "STAFF, ADMIN", "Tạm giữ giỏ 30 phút, mở lại hoặc hủy", "Trung bình", "AP-18"),
    ("FR-08", "Trả hàng kèm hoàn tiền", "ADMIN, STAFF", "Một bước: lập phiếu, nhập lại đúng lô, hoàn tiền", "Cao", "AP-12"),
    ("FR-09", "Tra cứu đơn, phiếu trả, thanh toán", "ADMIN, STAFF", "Lọc đơn, xem chi tiết, lịch sử thanh toán", "Cao",
     "AP-10, AP-11, AP-14"),
    ("FR-10", "Khuyến mãi 4 loại", "ADMIN", "Giảm %, giảm tiền, mua X tặng Y, giảm theo tổng đơn", "Cao", "AP-17"),
    ("FR-11", "Chốt ca thu ngân", "STAFF, ADMIN", "Tổng thu theo ca, nhập tiền đếm được, chênh lệch", "Cao", "AP-15"),
    ("FR-12", "Truy vết và thu hồi lô", "ADMIN", "Lô → các đơn đã bán; thu hồi phần còn tồn", "Cao", "AP-20"),
    ("FR-13", "Gợi ý mua kèm", "STAFF, ADMIN", "\"Đơn có A thường có B\"", "Trung bình", "AP-21"),
    ("FR-14", "Thống kê, top bán chạy, xuất CSV", "ADMIN (STAFF xem top bán)", "Doanh thu theo ngày, nhóm hàng; tiền khuyến mãi; "
     "top 10 bán chạy hôm nay", "Trung bình", "AP-19, AP-23"),
]
table(["Mã", "Tên yêu cầu", "Tác nhân", "Mô tả", "Ưu tiên", "Truy vết AP"], FR, [10, 19, 15, 32, 10, 14])

h2("3.2 Sơ đồ use case")
p("Hình 2 là sơ đồ use case tổng quát. Cột giữa là các chức năng cả ba vai trò cùng dùng; ADMIN kế thừa toàn bộ quyền "
  "của STAFF và WAREHOUSE.")
img("02_use_case_tong_quat.png", "Sơ đồ use case tổng quát", 15)

h2("3.3 Mô hình hóa nghiệp vụ (BPMN)")
img("03_bpmn_quy_trinh.png", "Sơ đồ BPMN quy trình nhập – bán – trả – chốt ca – thu hồi", 16.5)
p("Bảng dưới đây ghi dữ liệu được sinh ra hoặc đọc tại mỗi bước; đây là nguồn để lập bảng mẫu truy cập ở Phần 6.")
table(["Bước", "Vai trò", "Dữ liệu đọc", "Dữ liệu sinh ra"], [
    ("Lập phiếu nhập", "WAREHOUSE", "Nhà cung cấp, sản phẩm", "Phiếu nhập (DRAFT)"),
    ("Nhận hàng", "WAREHOUSE", "Phiếu nhập", "Lô hàng, tồn sản phẩm, thẻ kho RECEIPT, quan hệ lô–NCC"),
    ("Tính giá ở quầy", "STAFF", "Sản phẩm đang bán, khuyến mãi hiệu lực", "Giỏ đã tính giá (tạm)"),
    ("Thu tiền, tạo đơn", "STAFF", "Lô còn hạn (FEFO)", "Đơn hàng, cấp phát lô, thanh toán, thẻ kho SALE, quan hệ mua, top bán"),
    ("Treo đơn", "STAFF", "Giỏ hiện tại", "Đơn treo (30 phút)"),
    ("Trả hàng", "STAFF", "Đơn, cấp phát lô", "Phiếu trả, hoàn tiền, thẻ kho RETURN/RETURN_DAMAGED"),
    ("Chốt ca", "STAFF", "Thanh toán theo thu ngân trong ngày", "Bản ghi chốt ca"),
    ("Thu hồi lô", "ADMIN", "Quan hệ lô → đơn", "Danh sách đơn đã bán từ lô, điều chỉnh giảm phần còn tồn"),
], [18, 14, 30, 38])

h2("3.4 Mô hình hóa chức năng")
h3("3.4.1 Sơ đồ hoạt động (Activity) – bán tại quầy")
img("04_activity_ban_hang.png", "Activity bán tại quầy, có nhánh đọc cache Redis và nhánh xử lý tranh chấp", 11)
h3("3.4.2 Sơ đồ tuần tự (Sequence)")
img("05_sequence_ban_hang.png", "Sequence tạo đơn bán, lifeline riêng cho từng CSDL", 16.5)
img("06_sequence_tra_hang.png", "Sequence trả hàng kèm hoàn tiền", 15)
h3("3.4.3 Sơ đồ trạng thái (State machine)")
img("07_state_machine.png", "Vòng đời đơn hàng và phiếu nhập", 16)
p("Cả hai trạng thái đều lưu trong MongoDB và chỉ đổi trong transaction, vì chúng quyết định tiền và tồn kho nên cần nhất quán mạnh.")
h3("3.4.4 Sơ đồ luồng dữ liệu (DFD)")
p("DFD mức 0 chính là sơ đồ ngữ cảnh ở Hình 1. DFD mức 1 phân rã thành 7 tiến trình; kho dữ liệu được ghi theo cấu trúc "
  "của từng CSDL (collection, bảng CQL, nhãn node, mẫu key).")
img("08_dfd_muc_1.png", "DFD mức 1", 16)

h2("3.5 Đặc tả use case")
p("Mỗi use case gồm tác nhân, tiền điều kiện, đầu vào, luồng chính, quy tắc, luồng thay thế/ngoại lệ, hậu điều kiện, "
  "dữ liệu đọc/ghi và mẫu truy cập liên quan.")


def uc(code_, name, actor, prio, pre, inp, flow, rules, exc, post, data, ap):
    h3(f"{code_}. {name}")
    kv([("Mã yêu cầu", code_), ("Tên chức năng", name), ("Tác nhân", actor), ("Độ ưu tiên", prio),
        ("Tiền điều kiện", pre), ("Đầu vào", inp), ("Luồng xử lý chính", flow), ("Quy tắc nghiệp vụ", rules),
        ("Luồng thay thế / ngoại lệ", exc), ("Hậu điều kiện", post), ("Dữ liệu đọc / ghi", data), ("Truy vết AP", ap)])


uc("FR-01", "Tài khoản và phiên đăng nhập", "Mọi người dùng nội bộ; ADMIN xem lịch sử đăng nhập của mọi người", "Must",
   "Tài khoản tồn tại và đang hoạt động.",
   "Tên đăng nhập, mật khẩu; refresh token khi làm mới hoặc đăng xuất; họ tên, email, SĐT; mật khẩu hiện tại và mới; tháng cần xem lịch sử.",
   "1. Đăng nhập: tìm tài khoản theo tên đăng nhập, kiểm tra chưa bị khóa, so khớp mật khẩu BCrypt; phát access token (15 phút) "
   "và refresh token (7 ngày), trả về vai trò.\n"
   "2. Mỗi lần đăng nhập (thành công hoặc thất bại) ghi lịch sử: thời điểm, IP, trình duyệt.\n"
   "3. Làm mới: băm refresh token, kiểm tra còn hiệu lực; thu hồi token cũ, cấp cặp token mới (xoay vòng).\n"
   "4. Đăng xuất: thu hồi refresh token.\n"
   "5. Hồ sơ: xem và sửa họ tên, email (chuẩn hóa chữ thường), SĐT.\n"
   "6. Đổi mật khẩu: kiểm tra mật khẩu hiện tại, xác nhận khớp, ≤ 72 byte, khác mật khẩu cũ; lưu băm BCrypt và thu hồi mọi phiên.\n"
   "7. Xem lịch sử đăng nhập theo tháng, mới nhất trước.",
   "• Thông báo lỗi không nêu rõ sai tên hay sai mật khẩu.\n• Refresh token lưu dạng băm, có thời hạn, mỗi token dùng một lần.\n"
   "• Mọi API trừ đăng nhập, làm mới, đăng xuất cần token hợp lệ.\n• Không tự đổi vai trò hoặc trạng thái của mình.\n"
   "• Lịch sử đăng nhập tự xóa sau 90 ngày; người dùng chỉ xem được lịch sử của mình.",
   "• Sai thông tin: \"Tên đăng nhập hoặc mật khẩu không đúng\".\n• Tài khoản bị khóa: \"Tài khoản đã bị khóa\".\n"
   "• Token không hợp lệ, đã thu hồi, hết hạn: 401.\n• Mật khẩu hiện tại sai, xác nhận không khớp, trùng mật khẩu cũ, quá 72 byte: từ chối.\n"
   "• Email hoặc SĐT đã dùng: từ chối.",
   "Client có cặp token hợp lệ hoặc phiên đã kết thúc; hồ sơ, mật khẩu được cập nhật; lịch sử đăng nhập được ghi.",
   "Đọc/ghi: người dùng. Ghi/xóa: phiên đăng nhập (tạm). Ghi/đọc: lịch sử đăng nhập.", "AP-01, AP-02, AP-16")

uc("FR-02", "Quản lý người dùng", "ADMIN", "Must",
   "Đăng nhập với vai trò ADMIN.", "Tên đăng nhập, mật khẩu, họ tên, email, SĐT, vai trò, trạng thái; bộ lọc, trang, sắp xếp.",
   "1. Xem danh sách có lọc, phân trang, sắp xếp.\n2. Tạo: kiểm tra tên đăng nhập và email chưa tồn tại, vai trò hợp lệ.\n"
   "3. Cập nhật: kiểm tra trùng khi đổi; đổi vai trò, trạng thái.\n4. Khóa (xóa mềm) và thu hồi mọi phiên.",
   "• Vai trò là ADMIN, WAREHOUSE hoặc STAFF.\n• Không tự khóa chính mình; không khóa hoặc hạ quyền ADMIN hoạt động cuối cùng.",
   "• Trùng tên đăng nhập hoặc email: từ chối.\n• Không tìm thấy: 404.\n• Vi phạm quy tắc ADMIN cuối cùng: từ chối.",
   "Tài khoản được tạo, cập nhật hoặc khóa.", "Đọc/ghi: người dùng. Xóa: phiên đăng nhập.", "AP-01")

uc("FR-03", "Danh mục hàng hóa", "ADMIN (nhóm hàng); ADMIN, WAREHOUSE (sản phẩm, nhà cung cấp); mọi người dùng (xem, tra cứu)", "Must",
   "Đã đăng nhập; khi tạo sản phẩm thì nhóm hàng và nhà cung cấp đã tồn tại.",
   "Sản phẩm: tên, SKU, giá bán, nhóm hàng, nhà cung cấp mặc định, ngưỡng đặt lại, ảnh, thuộc tính riêng (ví dụ dung tích, bảo hành), "
   "trạng thái. Nhóm hàng: tên, mô tả. Nhà cung cấp: tên, SĐT, email, địa chỉ. Tra cứu: từ khóa, nhóm, khoảng giá, trang, sắp xếp.",
   "1. Nhóm hàng, nhà cung cấp: xem, tạo, sửa có kiểm tra trùng; xóa khi không còn sản phẩm liên quan.\n"
   "2. Sản phẩm: tạo (kiểm tra SKU chưa tồn tại), cập nhật, ngừng kinh doanh (xóa mềm); mỗi lần ghi xóa bộ nhớ đệm danh sách sản phẩm.\n"
   "3. Tra cứu theo tên hoặc SKU; lọc theo nhóm, giá; phân trang, sắp xếp.\n"
   "4. Danh sách sản phẩm đang bán cho quầy (đọc bộ nhớ đệm).\n"
   "5. Cảnh báo: sản phẩm có tồn khả dụng ≤ ngưỡng đặt lại; lô đã hết hạn còn tồn.",
   "• SKU duy nhất; tên nhóm hàng không trống, không trùng; email, SĐT nhà cung cấp duy nhất khi có giá trị.\n"
   "• Thuộc tính riêng là danh sách khóa–giá trị tự do theo nhóm hàng.\n"
   "• Tồn chỉ đổi qua lô, bán, trả; sản phẩm có phiên bản chống ghi đè đồng thời.\n"
   "• Nhà cung cấp ngừng hoạt động không nhận lô hoặc phiếu nhập mới.\n"
   "• Tồn khả dụng = tổng tồn còn lại của các lô còn hạn; lô hết hạn đúng hôm nay vẫn còn hạn.",
   "• Trùng SKU, tên, email, SĐT: từ chối.\n• Cố sửa tồn trực tiếp: từ chối.\n• Ghi đè đồng thời (phiên bản cũ): 409.\n"
   "• Xóa nhóm hàng hoặc NCC còn sản phẩm: từ chối.\n• Tham số trang, sắp xếp không hợp lệ: 400.",
   "Danh mục được cập nhật; danh sách tra cứu và cảnh báo trả về đúng bộ lọc.",
   "Đọc/ghi: sản phẩm, nhóm hàng, nhà cung cấp. Đọc: lô. Ghi/xóa: cache sản phẩm. Ghi sau: node sản phẩm, NCC trong đồ thị.",
   "AP-03, AP-04, AP-05, AP-06, AP-07, AP-08")

uc("FR-04", "Nhập hàng và điều chỉnh tồn", "ADMIN, WAREHOUSE", "Must",
   "Nhà cung cấp và sản phẩm đang hoạt động; khi điều chỉnh thì lô tồn tại.",
   "Phiếu nhập: nhà cung cấp, các dòng (sản phẩm, số lượng, giá vốn). Nhận hàng: số thực nhận, ngày sản xuất, hạn dùng từng dòng. "
   "Điều chỉnh: lô, số lượng giảm, lý do.",
   "1. Tạo phiếu nhập DRAFT; khi còn DRAFT được sửa hoặc xóa.\n2. Hàng về: mở phiếu, nhập số thực nhận và hạn dùng từng dòng, bấm \"Nhận hàng\".\n"
   "3. Trong một giao dịch: tạo lô (mã sinh tự động), cộng tồn, đặt phiếu RECEIVED.\n4. Ghi thẻ kho RECEIPT và quan hệ lô–nhà cung cấp.\n"
   "5. Ngoài phiếu, cho phép nhập lô lẻ cho hàng phát sinh (cùng quy tắc).\n"
   "6. Điều chỉnh giảm: trừ tồn lô và tồn sản phẩm trong một giao dịch; ghi thẻ kho ADJUSTMENT (số âm), ghi chú \"Lô <mã>: <lý do>\".",
   "• Số lượng > 0; ngày sản xuất không ở tương lai; hạn dùng không trước hôm nay và ngày sản xuất.\n"
   "• Mã lô, mã phiếu duy nhất, sinh bằng bộ đếm nguyên tử.\n• Phiếu RECEIVED không sửa, không xóa được.\n"
   "• Điều chỉnh chỉ giảm (hao hụt, hư hỏng, hết hạn, thu hồi); muốn tăng phải nhập lô. Lý do bắt buộc; không vượt tồn lô.",
   "• Ngày không hợp lệ, sản phẩm hoặc NCC ngừng hoạt động: từ chối.\n• Nhận lại phiếu đã RECEIVED: trả kết quả cũ, không cộng tồn lần hai.\n"
   "• Điều chỉnh vượt tồn hoặc thiếu lý do: từ chối.",
   "Có lô mới và tồn tăng, hoặc tồn giảm; thẻ kho có dòng RECEIPT hoặc ADJUSTMENT.",
   "Ghi: phiếu nhập, lô, sản phẩm, bộ đếm. Ghi sau: thẻ kho, đồ thị.", "AP-08, AP-22, AP-24")

uc("FR-05", "Thẻ kho", "Hệ thống ghi; mọi người dùng xem", "Must",
   "Có thay đổi tồn kho.", "Sản phẩm, tháng.",
   "1. Mỗi thay đổi tồn sinh một dòng biến động: loại, số lượng, lô, chứng từ, người thực hiện, thời điểm.\n"
   "2. Người dùng xem thẻ kho theo sản phẩm, mặc định tháng hiện tại, chọn được tháng khác.",
   "• RECEIPT, RETURN tăng tồn; SALE, ADJUSTMENT giảm; RETURN_DAMAGED không đổi tồn bán.\n• Chỉ thêm, không sửa, không xóa.\n"
   "• Tổng RECEIPT + SALE + RETURN + ADJUSTMENT của sản phẩm bằng tồn sản phẩm.",
   "• Sản phẩm không tồn tại: 404.", "Mọi thay đổi tồn có dòng tương ứng.", "Ghi/đọc: thẻ kho.", "AP-13")

uc("FR-06", "Bán tại quầy", "ADMIN, STAFF", "Must",
   "Có ít nhất một dòng hàng; sản phẩm đang bán và đủ tồn.",
   "Khóa yêu cầu request_key, danh sách sản phẩm và số lượng, phương thức CASH hoặc BANK_TRANSFER, "
   "mã giao dịch với chuyển khoản, mã báo giá quoteHash.",
   "1. Thu ngân quét hàng; hệ thống tính giá: áp khuyến mãi (FR-10), thêm dòng quà, trả quoteHash.\n"
   "2. Thu ngân chọn phương thức thanh toán và xác nhận.\n3. Kiểm tra request_key; tính lại giá, so quoteHash.\n"
   "4. Cấp phát FEFO cho từng dòng: lọc lô còn tồn và còn hạn; sắp hạn dùng tăng dần, lô không hạn xếp cuối, rồi ngày nhận, mã lô; "
   "lấy lần lượt đến khi đủ, mỗi lần trừ chỉ thành công nếu tồn lô còn đủ.\n"
   "5. Trừ tồn, tạo đơn PAID kèm dòng cấp phát lô và thanh toán PAYMENT trong một giao dịch.\n"
   "6. Sau khi lưu: ghi thẻ kho SALE, sổ thanh toán, quan hệ mua; cộng top bán chạy; xóa cache sản phẩm.\n7. In hóa đơn.",
   "• Chỉ CASH, BANK_TRANSFER; chuyển khoản phải có mã giao dịch.\n• Đơn giá là giá hiện tại; tiền giảm được chia vào từng dòng (netAmount).\n"
   "• Hàng tặng là dòng 0 đồng nhưng vẫn trừ kho.\n• Không đủ tồn thì từ chối cả đơn, không cấp phát một phần; hai đơn đồng thời không được bán quá tồn.\n"
   "• Tổng cấp phát của dòng bằng số lượng bán.\n• Đơn đã lập không hủy; sai sót xử lý bằng trả hàng.",
   "• request_key trùng cùng nội dung: trả đơn cũ; khác nội dung: 409.\n• Giá hoặc khuyến mãi đổi so với báo giá: 409 PRICE_CHANGED, thu ngân tính lại.\n"
   "• Xung đột đồng thời khi trừ lô: thử lại tối đa 3 lần rồi báo thiếu tồn.\n"
   "• Thiếu tồn, sản phẩm ngừng bán, thiếu mã giao dịch: từ chối.",
   "Đơn đã thanh toán, kho đã trừ; gửi lặp không tạo đơn thứ hai.",
   "Đọc: sản phẩm, khuyến mãi (cache), lô. Ghi: đơn, lô, sản phẩm. Ghi sau: thẻ kho, sổ thanh toán, đồ thị, top bán.",
   "AP-03, AP-07, AP-09, AP-17, AP-19")

uc("FR-07", "Đơn treo tại quầy", "STAFF, ADMIN", "Should",
   "Giỏ hiện tại có ít nhất một dòng.", "Giỏ hàng, ghi chú để nhận lại đơn (vd: chị áo xanh).",
   "1. Bấm \"Treo đơn\": lưu giỏ, giỏ quầy trống để phục vụ khách khác.\n2. Danh sách đơn treo của thu ngân.\n"
   "3. Mở lại: giỏ quay về quầy và bị xóa khỏi danh sách (một thao tác nguyên tử).\n4. Hủy đơn treo.",
   "• Tự hết hạn sau 30 phút.\n• Không giữ tồn kho; giá được tính lại khi mở.\n• Mỗi thu ngân chỉ thấy đơn treo của mình.",
   "• Đơn treo đã hết hạn: báo không còn.\n• Bộ nhớ tạm không khả dụng: báo tạm thời không dùng được, vẫn bán bình thường.",
   "Giỏ được giữ hoặc khôi phục.", "Ghi/đọc/xóa: đơn treo (tạm).", "AP-18")

uc("FR-08", "Trả hàng kèm hoàn tiền", "ADMIN, STAFF", "Must",
   "Đơn PAID hoặc PARTIALLY_RETURNED, trong 7 ngày kể từ ngày mua (cấu hình).",
   "Mã đơn, request_key, dòng trả (dòng đơn, số lượng, restock, lý do), phương thức hoàn, mã tham chiếu với chuyển khoản.",
   "1. Kiểm tra điều kiện, thời hạn, request_key.\n2. Chia số lượng trả vào các lô đã cấp phát, lô hạn xa nhất trước.\n"
   "3. restock = true: cộng tồn lô và sản phẩm (RETURN). restock = false: cộng hàng hỏng (RETURN_DAMAGED).\n"
   "4. Tính tiền hoàn theo giá thực trả đã chia; tạo phiếu trả và ghi thanh toán REFUND cùng lúc.\n"
   "5. Đổi trạng thái đơn RETURNED hoặc PARTIALLY_RETURNED.",
   "• Số trả ≤ số mua − số đã trả.\n• Hàng luôn về đúng lô đã xuất; lô đã hết hạn bắt buộc restock = false.\n"
   "• Tiền hoàn = phần netAmount tương ứng, tổng hoàn không vượt số đã thu; không truy thu khuyến mãi theo tổng đơn.",
   "• Sai trạng thái, quá hạn, vượt số lượng: từ chối.\n• Hoàn chuyển khoản thiếu mã: từ chối.\n• request_key trùng khác nội dung: 409.",
   "Có phiếu trả và khoản hoàn; kho, hàng hỏng, trạng thái đơn cập nhật đúng; gửi lặp không hoàn hai lần.",
   "Đọc/ghi: đơn, lô, sản phẩm, phiếu trả. Ghi sau: thẻ kho, sổ thanh toán.", "AP-12")

uc("FR-09", "Tra cứu đơn, phiếu trả và thanh toán", "ADMIN, STAFF", "Must",
   "Đã đăng nhập với vai trò được phép.", "Mã đơn, khoảng ngày, trạng thái, phương thức, trang, sắp xếp.",
   "1. Lọc đơn, mới nhất trước, phân trang.\n2. Xem chi tiết đơn: dòng hàng, khuyến mãi, lô đã cấp phát.\n"
   "3. Xem phiếu trả của đơn.\n4. Xem lịch sử thanh toán và hoàn tiền.",
   "• Chỉ ADMIN, STAFF được xem.", "• Không có quyền: 403. • Không tìm thấy: 404.", "Trả về dữ liệu đúng quyền.",
   "Đọc: đơn, phiếu trả, sổ thanh toán.", "AP-10, AP-11, AP-14")

uc("FR-10", "Khuyến mãi 4 loại", "ADMIN (quản lý); hệ thống áp dụng khi bán", "Must",
   "Đăng nhập ADMIN.", "Tên, loại, thời gian bắt đầu/kết thúc, phạm vi (sản phẩm hoặc nhóm hàng), tham số theo loại, trạng thái.",
   "1. Tạo, sửa, bật/tắt khuyến mãi; xem gợi ý mua kèm (FR-13) để chọn cặp sản phẩm.\n"
   "2. Khi tính giá: với mỗi dòng chọn một khuyến mãi dòng có lợi nhất; thêm dòng quà nếu mua X tặng Y khác loại.\n"
   "3. Sau đó chọn một khuyến mãi theo tổng đơn có lợi nhất đạt mức tối thiểu.\n4. Chia tiền giảm vào từng dòng.",
   "• PERCENT: 0 < % ≤ 100. FIXED: giảm ≤ giá. BUY_X_GET_Y: buyQty, getQty > 0; quà cùng loại hoặc khác loại.\n"
   "• ORDER_THRESHOLD: minTotal > 0; giảm tiền hoặc % có mức trần.\n• Mỗi dòng tối đa 1 khuyến mãi dòng; mỗi đơn tối đa 1 khuyến mãi đơn.\n"
   "• Quà không đủ tồn thì không áp khuyến mãi đó và báo thu ngân.\n• Khuyến mãi lúc bán được lưu lại trong dòng đơn, không đổi khi khuyến mãi bị sửa.",
   "• Tham số sai, thời gian kết thúc trước bắt đầu: từ chối.", "Khuyến mãi có hiệu lực ngay ở quầy.",
   "Đọc/ghi: khuyến mãi; xóa cache khuyến mãi.", "AP-17")

uc("FR-11", "Chốt ca thu ngân", "STAFF (ca của mình); ADMIN (mọi thu ngân)", "Must",
   "Thu ngân có giao dịch trong ngày.", "Ngày, tiền mặt đếm được, ghi chú.",
   "1. Đồng bộ các giao dịch còn chờ của thu ngân.\n2. Hiện tiền mặt thu, chuyển khoản thu, tiền hoàn, tiền mặt phải có.\n"
   "3. Thu ngân nhập tiền đếm được; hệ thống tính chênh lệch và lưu bản chốt ca.\n4. ADMIN xem danh sách chốt ca theo ngày.",
   "• Mỗi thu ngân mỗi ngày một bản chốt; ADMIN được mở lại.\n• Tiền mặt phải có = thu tiền mặt − hoàn tiền mặt.",
   "• Đã chốt: từ chối, trừ khi ADMIN mở lại.", "Có bản chốt ca kèm chênh lệch.",
   "Đọc: sổ thanh toán theo thu ngân và ngày. Ghi: chốt ca.", "AP-15")

uc("FR-12", "Truy vết và thu hồi lô", "ADMIN", "Must",
   "Lô tồn tại.", "Mã lô.",
   "1. Nhập mã lô; hệ thống trả các đơn đã lấy hàng từ lô, số lượng, thời điểm.\n"
   "2. Hiện tổng đã bán, còn tồn, nhà cung cấp của lô.\n3. Đánh dấu thu hồi: điều chỉnh giảm phần còn tồn (FR-04) và giữ danh sách đơn đã bán từ lô.",
   "• Kết quả gồm cả đơn đã trả hàng.",
   "• Đồ thị chưa đồng bộ: hiện cảnh báo và cho dựng lại từ dữ liệu gốc.", "Lô còn tồn bằng 0; có danh sách đơn đã bán từ lô.",
   "Đọc: quan hệ lô–đơn. Ghi: lô (điều chỉnh).", "AP-20")

uc("FR-13", "Gợi ý mua kèm", "STAFF, ADMIN", "Should",
   "Có lịch sử bán.", "Sản phẩm vừa thêm vào giỏ.",
   "1. Khi thêm sản phẩm vào giỏ, hiện tối đa 5 sản phẩm thường được mua cùng.\n2. ADMIN xem gợi ý ở trang khuyến mãi.",
   "• Xếp theo số đơn có cả hai sản phẩm.\n• Chỉ gợi ý sản phẩm đang bán.",
   "• Không có dữ liệu hoặc đồ thị không khả dụng: không hiện gợi ý, không chặn bán.", "Thu ngân thấy gợi ý.",
   "Đọc: quan hệ đơn–sản phẩm.", "AP-21")

uc("FR-14", "Thống kê bán hàng, top bán chạy và xuất CSV", "ADMIN; STAFF xem top bán chạy ở quầy", "Should",
   "Có đơn hàng.", "Khoảng ngày (7, 30 ngày hoặc tùy chọn).",
   "1. Doanh thu, số đơn theo ngày (biểu đồ cột).\n2. Doanh thu theo nhóm hàng; tổng tiền khuyến mãi; top sản phẩm trong kỳ.\n3. Xuất CSV.\n"
   "4. Top 10 bán chạy hôm nay: mỗi đơn cộng số lượng bán (không tính hàng tặng); hiện ở màn hình bán hàng và trang thống kê.",
   "• Doanh thu = tổng tiền thực thu − tiền hoàn.\n• Bảng xếp hạng trong ngày giữ 3 ngày.",
   "• Khoảng ngày quá 366 ngày: từ chối.\n• Bộ nhớ tạm không khả dụng: ẩn khung top bán chạy, thống kê vẫn chạy.",
   "Có số liệu, biểu đồ, file CSV và bảng xếp hạng trong ngày.",
   "Đọc: đơn hàng (gom nhóm). Ghi/đọc: bảng xếp hạng (tạm).", "AP-19, AP-23")

# ---------------------------------------------------------------- 4
h1("4. YÊU CẦU PHI CHỨC NĂNG")
p("Mỗi yêu cầu có con số và cách kiểm chứng; Chương 7 của báo cáo kiểm chứng lại. Độ trễ tính từ backend tới CSDL cloud "
  "qua Internet, nên đặt mục tiêu thực tế cho gói miễn phí.")
table(["Mã", "Nhóm", "Yêu cầu (đo được)", "Cách kiểm chứng"], [
    ("NFR-01", "Bảo mật – xác thực", "JWT không giữ phiên; mật khẩu BCrypt; refresh token lưu băm, dùng một lần.",
     "Không có mật khẩu gốc trong dữ liệu; token cũ bị từ chối sau khi làm mới; API thiếu token trả 401."),
    ("NFR-02", "Bảo mật – phân quyền", "Phân quyền theo vai trò cho từng API.", "100% API ghi có kiểm tra vai trò; sai quyền trả 403."),
    ("NFR-03", "Toàn vẹn tồn kho", "Không bán quá tồn khi nhiều thu ngân bán đồng thời.",
     "Hai yêu cầu cùng mua đơn vị cuối: đúng một thành công, tồn không âm (test đồng thời)."),
    ("NFR-04", "Tính lặp an toàn", "Nhập lô, tạo đơn, trả hàng, ghi sổ cái không tạo hiệu ứng thứ hai khi gửi lặp.",
     "Gửi lặp cùng request_key / mã phiếu: không thêm bản ghi, không đổi tồn hay tiền lần hai."),
    ("NFR-05", "Nhất quán", "Tiền, tồn kho, trạng thái đơn: nhất quán mạnh. Thẻ kho, sổ thanh toán, đồ thị: nhất quán sau, trễ ≤ 60 giây.",
     "Đo thời gian từ lúc tạo đơn đến lúc dòng sổ cái xuất hiện; mô tả write concern / consistency level."),
    ("NFR-06", "Truy vết", "Mọi thay đổi tồn có dòng thẻ kho; mọi khoản thu, hoàn có bản ghi thanh toán.",
     "Với mỗi sản phẩm: tổng RECEIPT + SALE + RETURN + ADJUSTMENT = tồn sản phẩm."),
    ("NFR-07", "Hiệu năng", "Danh sách sản phẩm ở quầy (có cache) p95 < 100 ms; tra cứu p95 < 300 ms; tạo đơn p95 < 1 s ở 20 yêu cầu/giây.",
     "k6 hoặc JMeter; so sánh có và không có cache Redis."),
    ("NFR-08", "Kiểm tra tham số", "Danh sách có phân trang, sắp xếp theo trường cho phép.", "Tham số sai trả 400 kèm thông báo rõ."),
    ("NFR-09", "Sẵn sàng / chịu lỗi", "Redis hoặc Neo4j không khả dụng: vẫn bán và trả hàng được; dữ liệu chờ tự đồng bộ ≤ 2 phút sau khi CSDL hoạt động lại.",
     "Đổi URL Redis/Neo4j sai, chạy kịch bản bán hàng, khôi phục rồi kiểm tra sổ cái và đồ thị."),
    ("NFR-10", "Sao lưu, phục hồi", "Có script sao lưu và khôi phục cho từng CSDL; khôi phục dữ liệu chính < 30 phút; dựng lại đồ thị từ dữ liệu gốc < 5 phút với 10 nghìn đơn.",
     "Kịch bản xóa dữ liệu → khôi phục → đếm lại số bản ghi."),
    ("NFR-11", "Bảo mật kết nối, chống injection", "Kết nối tới 4 CSDL dùng TLS; bí mật nằm trong biến môi trường; không nhận toán tử $ từ client; Cypher dùng tham số.",
     "Kiểm tra cấu hình; thử gửi {\"$gt\": \"\"} vào trường tìm kiếm."),
    ("NFR-12", "Khả năng mở rộng dữ liệu", "Partition Cassandra < 100 MB nhờ chia theo tháng; truy vấn chính trên MongoDB dùng index.",
     "Ước lượng kích thước partition; explain() cho thấy IXSCAN."),
    ("NFR-13", "Thời gian và ngày", "Hạn dùng là ngày lịch, múi giờ thống nhất.",
     "Lô hết hạn đúng hôm nay vẫn bán được; sang ngày hôm sau không còn được cấp phát."),
], [11, 16, 39, 34])

# ---------------------------------------------------------------- 5
h1("5. YÊU CẦU DỮ LIỆU")
h2("5.1 Đặc tính dữ liệu (3V)")
p("Ước lượng cho một siêu thị 5 quầy, khoảng 300 đơn mỗi ngày. Dữ liệu demo dùng khoảng 5 nghìn đơn trải đều 30 ngày.")
table(["Nhóm dữ liệu", "Volume", "Velocity", "Variety", "Vòng đời / TTL"], [
    ("Danh mục: sản phẩm, nhóm, NCC", "~2 nghìn sản phẩm, < 20 MB", "Đọc rất cao (mỗi lần quét), ghi < 1/phút",
     "Bán cấu trúc: thuộc tính riêng theo nhóm hàng", "Vĩnh viễn, xóa mềm"),
    ("Lô hàng, phiếu nhập", "~20 nghìn lô/năm", "Đọc mỗi lần bán; ghi vài chục/ngày", "Có cấu trúc, phiếu có nhiều dòng", "Vĩnh viễn"),
    ("Đơn hàng, phiếu trả", "~110 nghìn đơn/năm, 2–5 KB/đơn", "Ghi đỉnh 2 đơn/giây; đọc theo mã", "Lồng nhau: dòng, lô, thanh toán, khuyến mãi", "Vĩnh viễn"),
    ("Thẻ kho, sổ thanh toán", "~1.500 dòng/ngày, ~550 nghìn/năm", "Ghi liên tục, chỉ thêm; đọc theo sản phẩm/tháng, đơn, thu ngân/ngày",
     "Bản ghi theo thời gian", "Vĩnh viễn, không sửa"),
    ("Lịch sử đăng nhập", "~50/ngày", "Ghi mỗi lần đăng nhập", "Bản ghi theo thời gian", "TTL 90 ngày"),
    ("Phiên, cache, đơn treo, xếp hạng", "Vài trăm key, < 5 MB", "Đọc/ghi rất cao, < 10 ms", "Key-value, sorted set", "TTL 1 phút – 7 ngày"),
    ("Quan hệ đơn–sản phẩm–lô–NCC", "~110 nghìn node đơn, ~400 nghìn cạnh/năm", "Đọc vài chục/phút; ghi theo đơn",
     "Đồ thị", "Vĩnh viễn; dựng lại được"),
], [21, 20, 22, 20, 17])

h2("5.2 Mô hình khái niệm")
img("09_mo_hinh_khai_niem.png", "Mô hình khái niệm các thực thể", 16)

h2("5.3 Từ điển dữ liệu")
p("Kiểu \"Định danh\" do thiết kế từng CSDL quyết định (Chương 4). Tiền tệ lưu số thập phân chính xác, không dùng số thực.")


def ent(title, rows):
    h3(title)
    table(["Thuộc tính", "Kiểu", "Bắt buộc / ràng buộc", "Ví dụ"], rows, [24, 15, 37, 24])


ent("5.3.1 Người dùng", [
    ("user_id", "Định danh", "Khóa chính", "6731…a1"), ("username", "Chuỗi", "Bắt buộc, duy nhất", "thungan01"),
    ("password_hash", "Chuỗi", "Bắt buộc, BCrypt, mật khẩu gốc ≤ 72 byte", "$2a$10$…"), ("full_name", "Chuỗi", "Bắt buộc", "Nguyễn Văn A"),
    ("email, phone", "Chuỗi", "Duy nhất khi có giá trị", "a@shop.vn, 0901…"), ("role", "Liệt kê", "ADMIN, WAREHOUSE, STAFF", "STAFF"),
    ("active", "Logic", "Mặc định đúng; sai = đã khóa", "true")])
ent("5.3.2 Phiên đăng nhập (refresh token)", [
    ("token_hash", "Chuỗi", "Duy nhất, SHA-256 của token", "9f2c…"), ("user_id", "Định danh", "Bắt buộc", "6731…a1"),
    ("expires_at", "Thời điểm", "7 ngày sau khi cấp", "2026-11-14 09:00")])
ent("5.3.3 Lịch sử đăng nhập", [
    ("user_id, login_at", "Định danh, thời điểm", "Bắt buộc", "6731…a1, 2026-11-07 08:01"),
    ("success", "Logic", "Bắt buộc", "true"), ("ip, user_agent", "Chuỗi", "Tùy chọn", "113.161.x.x, Chrome"),
    ("—", "—", "Tự xóa sau 90 ngày", "—")])
ent("5.3.4 Nhóm hàng và nhà cung cấp", [
    ("category: name", "Chuỗi", "Bắt buộc, không trống, duy nhất", "Sữa"), ("category: description, active", "Chuỗi, logic", "Tùy chọn", "—"),
    ("supplier: name", "Chuỗi", "Bắt buộc", "Vinamilk"), ("supplier: phone, email", "Chuỗi", "Duy nhất khi có giá trị", "028…"),
    ("supplier: address, active", "Chuỗi, logic", "Tùy chọn; mặc định hoạt động", "—")])
ent("5.3.5 Sản phẩm", [
    ("product_id, sku", "Định danh, chuỗi", "Khóa chính; SKU bắt buộc, duy nhất", "SUA-TT-1L"), ("name", "Chuỗi", "Bắt buộc", "Sữa tươi 1L"),
    ("price", "Tiền tệ", "Bắt buộc, ≥ 0; đơn giá khi bán", "32000"), ("category_id, supplier_id", "Định danh", "Tham chiếu nhóm hàng, NCC mặc định", "—"),
    ("attributes", "Danh sách khóa–giá trị", "Tùy chọn, khác nhau theo nhóm hàng", "{dungTich: \"1L\"}"),
    ("stock_quantity, damaged_quantity", "Số nguyên", "≥ 0; chỉ đổi qua lô, bán, trả", "120, 2"), ("reorder_point", "Số nguyên", "Ngưỡng cảnh báo", "20"),
    ("image_url, active, version", "Chuỗi, logic, số nguyên", "Tùy chọn; xóa mềm; khóa lạc quan", "—")])
ent("5.3.6 Lô hàng", [
    ("batch_id, batch_code", "Định danh, chuỗi", "Khóa chính; mã duy nhất, sinh tự động", "L261107-0003"),
    ("product_id, supplier_id, purchase_order_id", "Định danh", "Sản phẩm bắt buộc; còn lại tùy chọn", "—"),
    ("received_qty", "Số nguyên", "> 0", "100"), ("remaining_qty, damaged_qty", "Số nguyên", "≥ 0", "80, 0"),
    ("unit_cost", "Tiền tệ", "Giá vốn", "24000"), ("mfg_date, expiry_date", "Ngày", "NSX không ở tương lai; HSD tùy chọn, không trước NSX", "2026-11-01, 2026-11-20"),
    ("received_at, received_by", "Thời điểm, chuỗi", "Bắt buộc", "—")])
ent("5.3.7 Phiếu nhập", [
    ("po_code, supplier_id", "Chuỗi, định danh", "Mã duy nhất; NCC bắt buộc", "PN261107-0001"),
    ("status", "Liệt kê", "DRAFT, RECEIVED; phiếu nháp được sửa hoặc xóa", "DRAFT"),
    ("lines", "Danh sách", "≥ 1 dòng: sản phẩm, số đặt, giá vốn, số thực nhận, lô sinh ra", "—"),
    ("created_by, created_at, received_at", "Chuỗi, thời điểm", "—", "—")])
ent("5.3.8 Đơn hàng, dòng đơn và cấp phát lô", [
    ("order_code, request_key", "Chuỗi", "Duy nhất; request_key chống tạo trùng", "HD261107-0042"),
    ("cashier", "Tham chiếu", "Bắt buộc", "—"),
    ("ordered_at, status", "Thời điểm, liệt kê", "PAID, PARTIALLY_RETURNED, RETURNED", "PAID"),
    ("payment_method, transaction_ref", "Liệt kê, chuỗi", "CASH, BANK_TRANSFER; mã giao dịch bắt buộc với CK", "CASH"),
    ("gross_total, discount_total, total", "Tiền tệ", "total = gross − discount = tổng netAmount", "120000, 20000, 100000"),
    ("order_promotion", "Ảnh chụp khuyến mãi", "Tối đa 1 khuyến mãi theo tổng đơn", "—"),
    ("dòng: product, quantity, unit_price", "—", "Số lượng > 0; đơn giá chốt lúc bán", "Sữa tươi 1L, 3, 30000"),
    ("dòng: gross_amount, net_amount, is_gift", "Tiền tệ, logic", "net_amount = tiền thực trả của dòng", "90000, 60000, false"),
    ("dòng: promotion, returned_quantity", "Ảnh chụp, số nguyên", "Tối đa 1 khuyến mãi dòng; ≤ số lượng", "Mua 2 tặng 1, 0"),
    ("cấp phát: batch, quantity, returned_quantity", "—", "Tổng theo dòng = số lượng dòng", "L261101-0003, 3, 0")])
ent("5.3.9 Ghi nhận thanh toán", [
    ("type", "Liệt kê", "PAYMENT, REFUND", "PAYMENT"), ("method, amount", "Liệt kê, tiền tệ", "amount > 0", "CASH, 100000"),
    ("reference", "Chuỗi", "Cùng đơn, loại, mã thì không ghi trùng", "POS-HD261107-0042"),
    ("return_id, cashier, created_at", "—", "Mỗi phiếu trả có đúng một khoản hoàn", "—")])
ent("5.3.10 Phiếu trả và dòng phiếu trả", [
    ("return_code, order_id, request_key", "Chuỗi, định danh", "request_key duy nhất toàn hệ thống", "PT261108-0001"),
    ("refund_amount, refund_method, refund_reference", "Tiền tệ, liệt kê, chuỗi", "Hoàn cùng lúc lập phiếu", "20000, CASH"),
    ("dòng: order line, batch, quantity", "—", "Quantity > 0; duy nhất theo (dòng, lô)", "—"),
    ("dòng: restock, reason, refund_amount", "Logic, chuỗi, tiền", "Lý do bắt buộc", "true, \"Khách đổi ý\"")])
ent("5.3.11 Biến động kho", [
    ("product_id, batch_code", "Định danh, chuỗi", "Bắt buộc", "—"),
    ("movement_type", "Liệt kê", "RECEIPT, SALE, RETURN, RETURN_DAMAGED, ADJUSTMENT", "SALE"),
    ("quantity", "Số nguyên", "Có dấu theo loại", "-3"), ("ref_type, ref_code", "Chuỗi", "Chứng từ gốc", "ORDER, HD261107-0042"),
    ("note, performed_by, created_at", "Chuỗi, thời điểm", "Chỉ thêm, không sửa", "—")])
ent("5.3.12 Khuyến mãi", [
    ("name, type", "Chuỗi, liệt kê", "PERCENT, FIXED, BUY_X_GET_Y, ORDER_THRESHOLD", "Mua 2 tặng 1 sữa"),
    ("starts_at, ends_at, active", "Thời điểm, logic", "ends_at > starts_at", "—"),
    ("scope", "Danh sách", "Sản phẩm hoặc nhóm hàng áp dụng", "[SUA-TT-1L]"),
    ("rule", "Cấu trúc theo loại", "{percent} / {amount} / {buyQty, getQty, giftProductId} / {minTotal, amount | percent, maxDiscount}",
     "{buyQty: 2, getQty: 1}")])
ent("5.3.13 Chốt ca", [
    ("cashier_id, day", "Định danh, ngày", "Duy nhất theo cặp", "—"),
    ("expected_cash, expected_bank, refunds", "Tiền tệ", "Tính từ sổ thanh toán", "5.200.000, 3.100.000, 50.000"),
    ("counted_cash, difference, note, closed_at", "Tiền tệ, chuỗi, thời điểm", "difference = counted − expected_cash", "5.150.000, -50.000")])

# ---------------------------------------------------------------- 6
h1("6. MẪU TRUY CẬP DỮ LIỆU (ACCESS PATTERNS)")
p("Bảng liệt kê mọi câu hỏi ứng dụng gửi tới CSDL, lập trước khi thiết kế. Chương 4 của báo cáo ánh xạ từng dòng sang "
  "cấu trúc dữ liệu cụ thể (collection + index, bảng CQL, mẫu Cypher, mẫu key); mục 8 tóm tắt ánh xạ đó.")
AP = [
    ("AP-01", "Tìm tài khoản theo tên đăng nhập", "Đọc", "username", "Cao", "< 50 ms", "Strong"),
    ("AP-02", "Lưu, kiểm tra, xoay vòng refresh token", "Đọc/Ghi", "tokenHash", "Cao", "< 10 ms", "Strong"),
    ("AP-03", "Danh sách sản phẩm đang bán ở quầy", "Đọc", "—", "Rất cao", "< 50 ms", "Eventual (5 phút)"),
    ("AP-04", "Tìm sản phẩm theo tên hoặc SKU", "Đọc", "từ khóa", "Rất cao", "< 100 ms", "Strong"),
    ("AP-05", "Lọc sản phẩm theo nhóm, giá, sắp xếp, phân trang", "Đọc", "nhóm, khoảng giá, trang", "Cao", "< 100 ms", "Strong"),
    ("AP-06", "Sản phẩm sắp hết hàng", "Đọc", "—", "Trung bình", "< 100 ms", "Eventual (1 phút)"),
    ("AP-07", "Lô còn hạn của sản phẩm theo FEFO", "Đọc", "productId, hôm nay", "Rất cao", "< 20 ms", "Strong"),
    ("AP-08", "Lô hết hạn còn tồn", "Đọc", "hôm nay", "Thấp", "< 200 ms", "Strong"),
    ("AP-09", "Tạo đơn: trừ lô, tạo đơn, thanh toán", "Ghi", "giỏ hàng, request_key", "Cao", "< 300 ms", "Strong"),
    ("AP-10", "Chi tiết đơn theo mã", "Đọc", "orderCode", "Cao", "< 50 ms", "Strong"),
    ("AP-11", "Danh sách đơn theo thời gian, trạng thái", "Đọc", "khoảng ngày, status, trang", "Trung bình", "< 100 ms", "Strong"),
    ("AP-12", "Lập phiếu trả kèm hoàn tiền", "Ghi", "orderId, dòng trả", "Trung bình", "< 300 ms", "Strong"),
    ("AP-13", "Thẻ kho của sản phẩm theo tháng", "Đọc", "productId, tháng", "Trung bình", "< 100 ms", "Eventual"),
    ("AP-14", "Lịch sử thanh toán của đơn", "Đọc", "orderId", "Trung bình", "< 50 ms", "Eventual"),
    ("AP-15", "Tổng thu của thu ngân trong ngày", "Đọc", "cashierId, ngày", "Thấp", "< 100 ms", "Eventual (đồng bộ trước khi đọc)"),
    ("AP-16", "Lịch sử đăng nhập 90 ngày", "Đọc/Ghi", "userId, tháng", "Thấp", "< 100 ms", "Eventual"),
    ("AP-17", "Khuyến mãi đang hiệu lực", "Đọc", "thời điểm", "Rất cao", "< 20 ms", "Eventual (5 phút)"),
    ("AP-18", "Lưu, liệt kê, mở đơn treo", "Đọc/Ghi", "cashierId, holdId", "Cao", "< 10 ms", "Strong (1 key)"),
    ("AP-19", "Top 10 bán chạy hôm nay", "Đọc/Ghi", "ngày", "Cao", "< 10 ms", "Eventual"),
    ("AP-20", "Truy vết lô → đơn", "Đọc", "batchCode", "Thấp", "< 200 ms", "Eventual"),
    ("AP-21", "Gợi ý mua kèm (2 bước quan hệ)", "Đọc", "productId", "Cao", "< 200 ms", "Eventual"),
    ("AP-22", "Phiếu nhập theo trạng thái; nhận hàng", "Đọc/Ghi", "status, poId", "Trung bình", "< 300 ms", "Strong"),
    ("AP-23", "Thống kê doanh thu theo ngày, nhóm hàng", "Đọc", "khoảng ngày", "Thấp", "< 1 s", "Strong"),
    ("AP-24", "Sinh mã chứng từ", "Ghi", "loại, ngày", "Cao", "< 20 ms", "Strong"),
]
table(["Mã", "Mẫu truy cập", "Loại", "Tham số", "Tần suất", "Độ trễ", "Nhất quán"], AP, [10, 29, 9, 17, 11, 10, 14])

# ---------------------------------------------------------------- 7
h1("7. YÊU CẦU GIAO DIỆN")
h2("7.1 Giao diện người dùng")
p("Ứng dụng web một trang, tiếng Việt, dùng nội bộ. Màn hình chỉ hiện với vai trò được phép; sau đăng nhập ADMIN và STAFF "
  "vào màn hình Bán tại quầy, WAREHOUSE vào Tồn kho. Ảnh chụp màn hình thật được đưa vào Chương 6 của báo cáo.")
table(["Màn hình", "Vai trò", "Nội dung chính"], [
    ("Đăng nhập", "Tất cả", "Tên đăng nhập, mật khẩu; tự làm mới phiên"),
    ("Bán tại quầy", "STAFF, ADMIN", "Tìm/quét sản phẩm, giỏ có khuyến mãi và dòng quà, gợi ý mua kèm, top bán chạy, đơn treo, thanh toán, in hóa đơn"),
    ("Đơn hàng", "STAFF, ADMIN", "Lọc đơn, chi tiết, in lại hóa đơn, trả hàng một bước, lịch sử thanh toán"),
    ("Chốt ca", "STAFF, ADMIN", "Tổng thu trong ca, nhập tiền đếm được, chênh lệch; ADMIN xem mọi ca"),
    ("Sản phẩm", "Tất cả xem", "Lọc, phân trang, thuộc tính riêng, tab Thẻ kho theo tháng"),
    ("Nhóm hàng, Nhà cung cấp", "ADMIN (+WAREHOUSE với NCC)", "Danh sách, thêm, sửa, xóa"),
    ("Phiếu nhập", "WAREHOUSE, ADMIN", "Tạo phiếu nháp, nhận hàng sinh lô"),
    ("Tồn kho theo lô", "WAREHOUSE, ADMIN", "Lô theo sản phẩm, lô hết hạn, điều chỉnh giảm; ADMIN: truy vết và thu hồi"),
    ("Khuyến mãi", "ADMIN", "Form theo 4 loại, khung gợi ý mua kèm"),
    ("Thống kê", "ADMIN", "Biểu đồ doanh thu theo ngày, nhóm hàng; top bán chạy; xuất CSV"),
    ("Người dùng", "ADMIN", "Quản lý tài khoản, lịch sử đăng nhập"),
    ("Trạng thái hệ thống", "ADMIN", "Tình trạng 4 CSDL, dữ liệu chờ đồng bộ, dựng lại đồ thị"),
], [20, 22, 58])

h2("7.2 Giao diện API")
p("REST + JSON, tiền tố /api, xác thực Bearer JWT; tài liệu OpenAPI tại /swagger-ui. Danh sách nhóm endpoint:")
table(["Nhóm", "Endpoint chính", "FR"], [
    ("Xác thực", "POST /auth/login, /auth/refresh, /auth/logout; GET/PUT /auth/me; PUT /auth/password; GET /auth/me/logins?month=", "FR-01"),
    ("Người dùng", "GET/POST /users; GET/PUT/DELETE /users/{id}; GET /users/{id}/logins?month=; GET /roles", "FR-01, FR-02"),
    ("Danh mục", "/categories, /suppliers, /products (CRUD); GET /products/low-stock; GET /products/sku/{sku}", "FR-03"),
    ("Kho", "GET/POST /inventory/batches; POST /inventory/batches/{id}/adjustments; GET /inventory/batches/expired; "
            "GET /products/{id}/stock-movements?month=", "FR-03 → FR-05"),
    ("Phiếu nhập", "GET/POST /purchase-orders; PUT/DELETE /purchase-orders/{id}; POST /purchase-orders/{id}/receive", "FR-04"),
    ("Bán, trả", "POST /orders/quote; POST /orders; GET /orders; GET /orders/{id}; GET /orders/code/{code}; "
                 "POST /orders/{id}/returns; GET /orders/{id}/returns; GET /orders/{id}/payments", "FR-06, FR-08, FR-09"),
    ("Quầy", "GET/POST /pos/held; GET/DELETE /pos/held/{holdId}; GET /pos/products/{id}/panel", "FR-07, FR-13, FR-14"),
    ("Khuyến mãi", "GET/POST /promotions; PUT /promotions/{id}; PUT /promotions/{id}/active", "FR-10"),
    ("Chốt ca", "GET /shifts/me/today; POST /shifts/me/close; GET /shifts?day=", "FR-11"),
    ("Đồ thị", "GET /batches/{code}/trace; GET /products/{id}/also-bought; POST /admin/graph/rebuild", "FR-12, FR-13"),
    ("Thống kê", "GET /stats/sales?from=&to=; GET /stats/sales.csv; GET /stats/top-sales/today", "FR-14"),
    ("Vận hành", "GET /health/datastores; GET /admin/sync/status; POST /admin/sync/retry", "—"),
], [14, 70, 16])

h2("7.3 Giao diện phần cứng và phần mềm")
p("Không yêu cầu phần cứng chuyên dụng; máy quầy chỉ cần trình duyệt (có thể gắn máy quét mã vạch dạng bàn phím).")
table(["Thành phần", "Phiên bản / dịch vụ", "Công cụ quản trị (GUI)"], [
    ("Backend", "Java 21, Spring Boot 4.1, Spring Data MongoDB, Spring Data Redis, DataStax Java Driver 4.17, Neo4j Java Driver 5", "IntelliJ / VS Code, Bruno"),
    ("Frontend", "React 19, TypeScript, Vite", "Trình duyệt Chrome/Edge"),
    ("MongoDB", "MongoDB Atlas M0 (replica set 3 node), MongoDB 8.x", "MongoDB Compass"),
    ("Cassandra", "DataStax Astra DB Serverless (CQL)", "Astra CQL Console / DataStax Studio"),
    ("Neo4j", "Neo4j AuraDB Free 5.x", "Neo4j Browser / Workspace"),
    ("Redis", "Redis Cloud (hoặc Upstash) Redis 7.x", "RedisInsight"),
], [16, 54, 30])

h2("7.4 Giao diện truyền thông")
p("Client và server giao tiếp REST/JSON qua HTTPS. Token JWT gửi trong header "
  "Authorization. Backend kết nối 4 CSDL bằng driver chính thức qua TLS; chuỗi kết nối và mật khẩu đặt trong biến môi trường.")

# ---------------------------------------------------------------- 8
h1("8. MA TRẬN TRUY VẾT YÊU CẦU")
p("FR → AP → CSDL và cấu trúc đáp ứng → ca kiểm thử. Mã ca kiểm thử TC-xx được chi tiết ở Chương 7 của báo cáo.")
table(["FR", "AP", "CSDL / cấu trúc", "Màn hình", "TC"], [
    ("FR-01", "AP-01, 02, 16", "MongoDB users · Redis auth:refresh:*, auth:user-tokens:* · Cassandra login_history_by_user (TTL 90 ngày)",
     "Đăng nhập, Menu tài khoản", "TC-01"),
    ("FR-02", "AP-01", "MongoDB users", "Người dùng", "TC-02"),
    ("FR-03", "AP-03 → 08", "MongoDB products (text, {categoryId, price}), categories, suppliers, batches · Redis cache:products:*",
     "Sản phẩm, Nhóm hàng, NCC, Tồn kho", "TC-03"),
    ("FR-04", "AP-08, 22, 24", "MongoDB purchase_orders, batches, counters · Cassandra stock_movements_by_product · Neo4j :SUPPLIED_BY",
     "Phiếu nhập, Tồn kho", "TC-04"),
    ("FR-05", "AP-13", "Cassandra stock_movements_by_product", "Sản phẩm – Thẻ kho", "TC-05"),
    ("FR-06", "AP-03, 07, 09, 17, 19", "MongoDB orders (transaction), batches {productId, expirySortKey} · Redis cache, top:sales:* · "
     "Cassandra payments_* · Neo4j", "Bán tại quầy", "TC-06"),
    ("FR-07", "AP-18", "Redis pos:held:* (MULTI, Lua)", "Bán tại quầy", "TC-07"),
    ("FR-08", "AP-12", "MongoDB orders, returns, batches · Cassandra", "Đơn hàng", "TC-08"),
    ("FR-09", "AP-10, 11, 14", "MongoDB orders · Cassandra payments_by_order", "Đơn hàng", "TC-09"),
    ("FR-10", "AP-17", "MongoDB promotions (polymorphic) · Redis cache:promotions:active", "Khuyến mãi", "TC-10"),
    ("FR-11", "AP-15", "Cassandra payments_by_cashier_day · MongoDB shift_closings", "Chốt ca", "TC-11"),
    ("FR-12", "AP-20", "Neo4j (:Batch)<-[:TOOK_FROM]-(:Order), (:Batch)-[:SUPPLIED_BY]->(:Supplier)", "Tồn kho", "TC-12"),
    ("FR-13", "AP-21", "Neo4j (:Product)<-[:CONTAINS]-(:Order)-[:CONTAINS]->(:Product)", "Bán tại quầy, Khuyến mãi", "TC-13"),
    ("FR-14", "AP-19, 23", "MongoDB aggregation trên orders · Redis top:sales:{ngày} (sorted set)", "Thống kê, Bán tại quầy", "TC-14"),
], [10, 15, 45, 20, 10])

# ---------------------------------------------------------------- 9
h1("9. GIẢ ĐỊNH, RÀNG BUỘC, RỦI RO VÀ PHỤ LỤC")
h2("9.1 Giả định")
b("Mọi thay đổi tồn kho phát sinh từ lô hàng; tồn sản phẩm là số tổng hợp để hiển thị nhanh.",
  "Đơn giá và khuyến mãi lúc bán được chốt trong dòng đơn; thay đổi sau đó không ảnh hưởng đơn đã tạo.",
  "Thời hạn trả hàng mặc định 7 ngày, lấy từ cấu hình.",
  "Hệ thống không lưu hồ sơ khách hàng; mọi đơn là đơn bán lẻ.")
h2("9.2 Ràng buộc")
b("Thời gian: trong khuôn khổ đồ án học phần Dữ liệu NoSQL, nhóm 3 sinh viên.",
  "Công nghệ: dữ liệu nằm trên MongoDB, Cassandra, Neo4j và Redis; không dùng RDBMS; dùng gói cloud miễn phí.",
  "Pháp lý: số điện thoại, email của người dùng nội bộ là dữ liệu cá nhân; không đưa vào file sao lưu công khai hay log.")
h2("9.3 Rủi ro kỹ thuật")
table(["Rủi ro", "Biện pháp giảm thiểu"], [
    ("Không có giao dịch xuyên 4 CSDL; Cassandra, Neo4j có thể lệch MongoDB khi lỗi giữa chừng.",
     "MongoDB là nguồn sự thật; dữ liệu cần ghi sang CSDL khác được lưu vào hàng đợi đồng bộ trong cùng giao dịch, "
     "ghi lại theo khóa xác định nên thử lại an toàn; đồ thị dựng lại được từ MongoDB."),
    ("Bán quá tồn khi nhiều thu ngân cùng mua phần hàng cuối.",
     "Transaction nhiều document và cập nhật có điều kiện tồn lô ≥ số lấy; thử lại khi xung đột."),
    ("MongoDB xếp null lên đầu khi sắp tăng dần, sai thứ tự FEFO cho lô không có hạn.",
     "Lưu khóa sắp xếp hạn dùng (9999-12-31 cho lô không có hạn) và đánh index theo khóa này."),
    ("Mất dữ liệu Redis (gói miễn phí không lưu bền).",
     "Redis chỉ giữ phiên, cache, đơn treo, xếp hạng; mất thì đăng nhập lại, cache dựng lại từ MongoDB. "
     "Bộ đếm mã chứng từ và chống gửi lặp đặt ở MongoDB."),
    ("Cassandra chỉ truy vấn hiệu quả theo khóa đã thiết kế; partition quá lớn.",
     "Mỗi câu truy vấn một bảng; chia partition theo tháng; không dùng ALLOW FILTERING."),
    ("Gói Neo4j Aura Free giới hạn số node, tự tạm dừng khi không dùng lâu.",
     "Dữ liệu demo nằm trong giới hạn; mở instance trước buổi demo; tính năng đồ thị lỗi không chặn bán hàng."),
    ("Sai số tiền khi dùng số thực; chia khuyến mãi lệch tổng.",
     "Dùng Decimal128 (MongoDB) và decimal (Cassandra); làm tròn xuống đến đồng, phần dư dồn vào dòng cuối."),
    ("Độ trễ mạng tới CSDL cloud.", "Cache Redis cho dữ liệu đọc nhiều; gọi song song các CSDL độc lập; đặt mục tiêu NFR phù hợp."),
], [40, 60])
h2("9.4 Phụ lục – giá trị trạng thái")
table(["Nhóm", "Giá trị"], [
    ("Vai trò", "ADMIN, WAREHOUSE, STAFF"),
    ("Trạng thái đơn hàng", "PAID, PARTIALLY_RETURNED, RETURNED"),
    ("Trạng thái phiếu nhập", "DRAFT, RECEIVED"),
    ("Phương thức thanh toán", "CASH, BANK_TRANSFER"),
    ("Loại ghi nhận thanh toán", "PAYMENT, REFUND"),
    ("Loại biến động kho", "RECEIPT, SALE, RETURN, RETURN_DAMAGED, ADJUSTMENT"),
    ("Loại khuyến mãi", "PERCENT, FIXED, BUY_X_GET_Y, ORDER_THRESHOLD"),
], [35, 65])

CONTENT = C
