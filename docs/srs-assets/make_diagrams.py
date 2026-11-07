"""Vẽ các sơ đồ cho SRS v2.0. Chạy: python docs/srs-assets/make_diagrams.py"""
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Ellipse, FancyArrowPatch, FancyBboxPatch, Rectangle

OUT = Path(__file__).resolve().parent
plt.rcParams["font.family"] = "Arial"

DB_COLORS = {
    "mongo": "#D5F0D5",
    "cass": "#D6E6F7",
    "neo": "#F3DDF0",
    "redis": "#F9DADA",
    "app": "#FFF3CD",
    "plain": "#FFFFFF",
    "grey": "#EEEEEE",
}


def canvas(w: float, h: float):
    fig, ax = plt.subplots(figsize=(w, h))
    ax.set_xlim(0, w * 10)
    ax.set_ylim(0, h * 10)
    ax.axis("off")
    return fig, ax


def box(ax, x, y, w, h, text, color="plain", fs=9, bold=False, rounded=True, ls="-"):
    style = "round,pad=0.02,rounding_size=1.2" if rounded else "square,pad=0"
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle=style, fc=DB_COLORS.get(color, color),
                                ec="#333333", lw=1, ls=ls, zorder=3))
    ax.text(x + w / 2, y + h / 2, text, ha="center", va="center", fontsize=fs, zorder=4,
            fontweight="bold" if bold else "normal", wrap=True)


def arrow(ax, x1, y1, x2, y2, text="", fs=7.5, style="-|>", ls="-", color="#333333", rad=0.0, tpos=0.5, toff=(0, 1.2)):
    ax.add_patch(FancyArrowPatch((x1, y1), (x2, y2), arrowstyle=style, mutation_scale=10, lw=0.9,
                                 color=color, ls=ls, connectionstyle=f"arc3,rad={rad}"))
    if text:
        tx = x1 + (x2 - x1) * tpos + toff[0]
        ty = y1 + (y2 - y1) * tpos + toff[1]
        ax.text(tx, ty, text, ha="center", va="center", fontsize=fs,
                bbox=dict(fc="white", ec="none", pad=0.4, alpha=0.9))


def actor(ax, x, y, name, fs=9):
    ax.add_patch(plt.Circle((x, y + 5.2), 1.1, fc="white", ec="#333", lw=1))
    ax.plot([x, x], [y + 1.8, y + 4.1], color="#333", lw=1)
    ax.plot([x - 1.6, x + 1.6], [y + 3.4, y + 3.4], color="#333", lw=1)
    ax.plot([x, x - 1.3], [y + 1.8, y], color="#333", lw=1)
    ax.plot([x, x + 1.3], [y + 1.8, y], color="#333", lw=1)
    ax.text(x, y - 1.5, name, ha="center", va="top", fontsize=fs, fontweight="bold")


def save(fig, name):
    fig.savefig(OUT / name, dpi=200, bbox_inches="tight", facecolor="white")
    plt.close(fig)


def legend(ax, x, y):
    items = [("mongo", "MongoDB"), ("cass", "Cassandra"), ("neo", "Neo4j"), ("redis", "Redis")]
    for i, (c, n) in enumerate(items):
        ax.add_patch(Rectangle((x + i * 14, y), 2.5, 2, fc=DB_COLORS[c], ec="#333", lw=0.8))
        ax.text(x + i * 14 + 3.3, y + 1, n, va="center", fontsize=8)


# 1. Sơ đồ ngữ cảnh
def context():
    fig, ax = canvas(12, 6.5)
    box(ax, 45, 25, 30, 16, "Hệ thống quản lý\nbán hàng và tồn kho siêu thị\n(Spring Boot + React)", "app", 11, True)
    actor(ax, 10, 45, "ADMIN\n(Quản lý)")
    actor(ax, 10, 25, "STAFF\n(Thu ngân)")
    actor(ax, 10, 6, "WAREHOUSE\n(Thủ kho)")
    arrow(ax, 13, 49, 45, 38, "Quản trị, khuyến mãi, thu hồi lô,\nthống kê")
    arrow(ax, 13, 29, 45, 33, "Bán, trả hàng, đơn treo, chốt ca")
    arrow(ax, 13, 10, 45, 26, "Phiếu nhập, nhận lô,\nđiều chỉnh, thẻ kho", tpos=0.35, toff=(0, -3))
    arrow(ax, 45, 31, 15, 23, "Hóa đơn, gợi ý, top bán chạy", tpos=0.45, toff=(0, -2))
    arrow(ax, 52, 25, 16, 5, "Cảnh báo sắp hết hàng,\nlô hết hạn", rad=-0.25, tpos=0.6, toff=(4, -3))
    dbs = [(55, "MongoDB Atlas\n(nguồn sự thật)", "mongo"), (40, "Astra DB\n(Cassandra – sổ cái)", "cass"),
           (25, "Neo4j Aura\n(quan hệ)", "neo"), (10, "Redis Cloud\n(tạm, nhanh)", "redis")]
    for y, t, c in dbs:
        box(ax, 95, y - 4, 22, 9, t, c, 9)
        arrow(ax, 75, 33, 95, y, style="<|-|>")
    ax.text(60, 62, "Sơ đồ ngữ cảnh", ha="center", fontsize=12, fontweight="bold")
    save(fig, "01_so_do_ngu_canh.png")


# 2. Use case tổng quát
def usecase():
    fig, ax = canvas(13, 11)
    ax.add_patch(Rectangle((25, 3), 85, 102, fc="white", ec="#333", lw=1.2))
    ax.text(67.5, 102, "Hệ thống quản lý siêu thị", ha="center", fontsize=11, fontweight="bold")
    groups = {
        "STAFF": ["FR-06 Bán tại quầy", "FR-07 Đơn treo", "FR-08 Trả hàng, hoàn tiền", "FR-11 Chốt ca",
                  "FR-13 Xem gợi ý mua kèm"],
        "CHUNG": ["FR-01 Đăng nhập, hồ sơ", "FR-03 Tra cứu sản phẩm, cảnh báo",
                  "FR-05 Xem thẻ kho", "FR-09 Tra cứu đơn, thanh toán"],
        "WAREHOUSE": ["FR-04 Phiếu nhập, nhận lô", "FR-04 Điều chỉnh giảm tồn", "FR-03 Sản phẩm, nhà cung cấp"],
        "ADMIN": ["FR-02 Quản lý người dùng", "FR-03 Nhóm hàng", "FR-10 Khuyến mãi", "FR-12 Truy vết, thu hồi lô",
                  "FR-14 Thống kê, top bán, CSV", "FR-01 Lịch sử đăng nhập"],
    }
    pos = {}
    cols = {"STAFF": (45, 96), "CHUNG": (67.5, 96), "WAREHOUSE": (90, 96), "ADMIN": (67.5, 45)}
    for g, items in groups.items():
        cx, cy = cols[g]
        if g == "ADMIN":
            for i, it in enumerate(items):
                x = 50 if i < 4 else 88
                y = 40 - (i % 4) * 8.5 if i < 4 else 40 - (i - 4) * 8.5 - 4
                ax.add_patch(Ellipse((x, y), 30, 6.5, fc="#FFFFFF", ec="#333", lw=0.9))
                ax.text(x, y, it, ha="center", va="center", fontsize=7.8)
                pos[it] = (x, y)
            continue
        for i, it in enumerate(items):
            y = cy - 6 - i * 8.5
            ax.add_patch(Ellipse((cx, y), 22, 6.5, fc="#FFFFFF", ec="#333", lw=0.9))
            ax.text(cx, y, it.replace(" sản phẩm, ", " SP, "), ha="center", va="center", fontsize=7.2)
            pos[it] = (cx, y)
    actor(ax, 10, 72, "STAFF")
    actor(ax, 125, 72, "WAREHOUSE")
    actor(ax, 10, 22, "ADMIN")
    for it in groups["STAFF"]:
        arrow(ax, 13, 76, pos[it][0] - 10.5, pos[it][1], style="-", color="#666")
    for it in groups["WAREHOUSE"]:
        arrow(ax, 122, 76, pos[it][0] + 10.5, pos[it][1], style="-", color="#666")
    for it in groups["ADMIN"][:4]:
        x, y = pos[it]
        arrow(ax, 13, 26, x - 15, y, style="-", color="#666")
    actor(ax, 125, 22, "ADMIN")
    for it in groups["ADMIN"][4:]:
        x, y = pos[it]
        arrow(ax, 122, 26, x + 15, y, style="-", color="#666")
    ax.text(67.5, 5.5,"Cả 3 vai trò dùng chung cột giữa. ADMIN có toàn quyền của STAFF và WAREHOUSE.",
            ha="center", fontsize=8, style="italic")
    save(fig, "02_use_case_tong_quat.png")


# 3. BPMN swimlane
def bpmn():
    fig, ax = canvas(16, 9)
    lanes = [("Thủ kho\n(WAREHOUSE)", 66), ("Hệ thống", 44), ("Thu ngân\n(STAFF)", 22), ("Quản lý\n(ADMIN)", 0)]
    for name, y in lanes:
        ax.add_patch(Rectangle((0, y), 160, 22, fc="white", ec="#333", lw=1))
        ax.add_patch(Rectangle((0, y), 12, 22, fc="#F2F2F2", ec="#333", lw=1))
        ax.text(6, y + 11, name, ha="center", va="center", fontsize=8.5, fontweight="bold")
    ax.add_patch(plt.Circle((17, 77), 2, fc="white", ec="#333"))
    steps = [
        (22, 72, "Tạo phiếu nhập\n(DRAFT)", "plain"),
        (44, 72, "Nhận hàng: nhập\nSL, NSX, HSD", "plain"),
        (44, 50, "Tạo lô, cộng tồn\n[Mongo] + thẻ kho\n[Cassandra]", "mongo"),
        (22, 28, "Quét hàng, tính giá\n(khuyến mãi)", "plain"),
        (44, 28, "Thu tiền\nCASH / CK", "plain"),
        (66, 50, "FEFO trừ lô, tạo đơn\n[Mongo] → sổ thanh toán\n[Cassandra], graph [Neo4j]", "mongo"),
        (88, 28, "Khách trả hàng:\nlập phiếu + hoàn tiền", "plain"),
        (110, 50, "Nhập lại đúng lô /\nghi hàng hỏng [Mongo]", "mongo"),
        (110, 28, "Cuối ca: chốt ca\n(đếm tiền két)", "plain"),
        (132, 50, "Tổng thu theo ca\n[Cassandra]", "cass"),
        (66, 6, "Tạo khuyến mãi\n[Mongo + Redis]", "plain"),
        (110, 6, "Lô lỗi: truy vết,\nthu hồi [Neo4j]", "neo"),
        (132, 6, "Xem chốt ca,\nthống kê", "plain"),
    ]
    centers = {}
    for x, y, t, c in steps:
        box(ax, x, y, 18, 10, t, c, 7)
        centers[t] = (x, y)
    def c(i):
        x, y, *_ = steps[i]
        return x, y
    links = [(0, 1), (1, 2), (3, 4), (7, 8), (8, 9)]
    arrow(ax, 19, 77, 22, 77)
    for a, b in links:
        ax1, ay1 = c(a)
        bx, by = c(b)
        if ay1 == by:
            arrow(ax, ax1 + 18, ay1 + 5, bx, by + 5)
        elif by < ay1:
            arrow(ax, ax1 + 9, ay1, bx + 9, by + 10)
        else:
            arrow(ax, ax1 + 9, ay1 + 10, bx + 9, by)
    arrow(ax, 62, 33, 66, 50)
    arrow(ax, 84, 55, 88, 38, ls="--")
    arrow(ax, 97, 38, 110, 50)
    arrow(ax, 119, 50, 119, 38)
    arrow(ax, 70, 16, 31, 28, "áp khi tính giá", ls="--", rad=-0.1)
    arrow(ax, 141, 50, 141, 16, ls="--")
    ax.add_patch(plt.Circle((154, 55), 2, fc="white", ec="#333", lw=2.2))
    arrow(ax, 150, 55, 152, 55)
    save(fig, "03_bpmn_quy_trinh.png")


# 4. Activity bán hàng
def activity():
    fig, ax = canvas(9, 13)
    y = 125
    ax.add_patch(plt.Circle((45, y), 1.8, fc="#333"))
    seq = [
        ("Thu ngân quét SKU / tìm sản phẩm", "app"),
        ("Đọc danh sách sản phẩm: cache Redis?\ncó → dùng cache · không → MongoDB rồi ghi cache", "redis"),
        ("Gọi quote: PromotionEngine tính khuyến mãi,\nthêm dòng quà, trả quoteHash", "app"),
        ("Chọn phương thức thanh toán\n(CK bắt buộc mã giao dịch)", "app"),
        ("POST /orders: mở transaction MongoDB", "mongo"),
        ("requestKey đã có?", "diamond"),
        ("Cấp phát FEFO: updateOne có điều kiện\nremainingQty ≥ số lấy", "mongo"),
        ("Đủ tồn?", "diamond"),
        ("Tạo đơn (dòng hàng, lô, thanh toán nhúng)\n+ ghi sync_queue → COMMIT", "mongo"),
        ("Sau commit: ghi sổ Cassandra, MERGE Neo4j,\nZINCRBY top bán, xóa cache sản phẩm", "cass"),
        ("In hóa đơn", "app"),
    ]
    y -= 4
    prev = (45, y + 2)
    for t, c in seq:
        y -= 9.5
        if c == "diamond":
            ax.add_patch(plt.Polygon([[45, y + 4.5], [55, y], [45, y - 4.5], [35, y]], fc="white", ec="#333"))
            ax.text(45, y, t, ha="center", va="center", fontsize=8)
            arrow(ax, prev[0], prev[1], 45, y + 4.5)
            prev = (45, y - 4.5)
            if t.startswith("requestKey"):
                box(ax, 62, y - 3, 26, 6, "Cùng nội dung: trả đơn cũ\nKhác: lỗi 409", "grey", 7.5)
                arrow(ax, 55, y, 62, y, "có")
            else:
                box(ax, 62, y - 3, 26, 6, "Hủy transaction,\nthử lại tối đa 3 lần → báo thiếu tồn", "grey", 7.5)
                arrow(ax, 55, y, 62, y, "không")
        else:
            box(ax, 20, y - 3.5, 50, 7, t, c, 8)
            arrow(ax, prev[0], prev[1], 45, y + 3.5)
            prev = (45, y - 3.5)
    ax.add_patch(plt.Circle((45, y - 9), 1.8, fc="white", ec="#333", lw=2.2))
    arrow(ax, prev[0], prev[1], 45, y - 7.2)
    legend(ax, 15, 1)
    save(fig, "04_activity_ban_hang.png")


# 5. Sequence bán hàng
def sequence():
    fig, ax = canvas(15, 10.5)
    lifes = [("Thu ngân\n(React)", 8), ("Backend\nOrderService", 32), ("MongoDB", 56), ("Redis", 80),
             ("Cassandra", 104), ("Neo4j", 128)]
    cmap = {"MongoDB": "mongo", "Redis": "redis", "Cassandra": "cass", "Neo4j": "neo"}
    for n, x in lifes:
        box(ax, x - 9, 95, 18, 7, n, cmap.get(n, "app"), 8.5, True)
        ax.plot([x, x], [6, 95], ls="--", color="#888", lw=0.8)
    msgs = [
        (8, 32, "POST /orders/quote (giỏ)"),
        (32, 80, "GET cache:promotions:active"),
        (32, 8, "giỏ đã tính giá + quoteHash"),
        (8, 32, "POST /orders (requestKey, quoteHash, CASH)"),
        (32, 56, "startTransaction · tìm requestKey"),
        (32, 56, "find lô FEFO · updateOne($gte) trừ lô"),
        (32, 56, "insert orders, update products, insert sync_queue"),
        (32, 56, "commitTransaction"),
        (32, 8, "201 Created (hóa đơn)"),
        (32, 104, "@Async: INSERT stock_movements, payments_*"),
        (32, 128, "MERGE Order/Product/Batch"),
        (32, 56, "sync_queue → DONE"),
        (32, 80, "ZINCRBY top:sales · DEL cache:products"),
    ]
    y = 88
    for a, b, t in msgs:
        dashed = b == 8
        arrow(ax, a, y, b, y, t, fs=7.5, ls="--" if dashed else "-", toff=(0, 1.6))
        y -= 6.3
    ax.add_patch(Rectangle((29, 41), 31, 25, fc="none", ec="#2E7D32", lw=1, ls=":"))
    ax.text(59.5, 64.5, "transaction MongoDB", fontsize=7, color="#2E7D32", ha="right")
    save(fig, "05_sequence_ban_hang.png")


# 6. Sequence trả hàng
def sequence_return():
    fig, ax = canvas(13, 8)
    lifes = [("Thu ngân", 8), ("Backend\nReturnService", 34), ("MongoDB", 62), ("Cassandra", 90), ("Redis", 114)]
    cmap = {"MongoDB": "mongo", "Redis": "redis", "Cassandra": "cass"}
    for n, x in lifes:
        box(ax, x - 9, 70, 18, 7, n, cmap.get(n, "app"), 8.5, True)
        ax.plot([x, x], [5, 70], ls="--", color="#888", lw=0.8)
    msgs = [
        (8, 34, "POST /orders/{id}/returns (dòng trả, restock, CK/CASH)"),
        (34, 62, "startTransaction · đọc đơn, kiểm tra hạn 7 ngày, requestKey"),
        (34, 62, "cộng lô (restock) hoặc damagedQty · cập nhật returnedQty"),
        (34, 62, "insert returns · push payments REFUND · đổi status · sync_queue"),
        (34, 62, "commitTransaction"),
        (34, 8, "201 phiếu trả + số tiền hoàn"),
        (34, 90, "INSERT movements RETURN/RETURN_DAMAGED, payments REFUND"),
        (34, 114, "DEL cache:products"),
    ]
    y = 63
    for a, b, t in msgs:
        arrow(ax, a, y, b, y, t, fs=7.5, ls="--" if b == 8 else "-", toff=(0, 1.6))
        y -= 7
    save(fig, "06_sequence_tra_hang.png")


# 7. State machine
def states():
    fig, ax = canvas(14, 7)
    ax.text(30, 66, "Đơn hàng (orders.status)", ha="center", fontsize=10, fontweight="bold")
    ax.add_patch(plt.Circle((5, 45), 1.6, fc="#333"))
    box(ax, 13, 41, 16, 8, "PAID", "mongo", 9, True)
    box(ax, 13, 16, 22, 8, "PARTIALLY_RETURNED", "mongo", 8.5, True)
    box(ax, 44, 41, 16, 8, "RETURNED", "mongo", 9, True)
    arrow(ax, 6.6, 45, 13, 45, "tạo đơn\n(đã thu tiền)", toff=(0, 3))
    arrow(ax, 21, 41, 22, 24, "trả một phần", toff=(-6, 0))
    arrow(ax, 29, 45, 44, 45, "trả hết", toff=(0, 1.5))
    arrow(ax, 35, 22, 48, 41, "trả hết phần còn lại", toff=(8, 0))
    ax.text(24, 12, "trả thêm: giữ nguyên trạng thái", ha="center", fontsize=7.5)
    ax.text(31, 4, "Lưu trong MongoDB, đổi trạng thái trong transaction (nhất quán mạnh).",
            ha="center", fontsize=7.5, style="italic")

    ax.text(105, 66, "Phiếu nhập (purchase_orders.status)", ha="center", fontsize=10, fontweight="bold")
    ax.add_patch(plt.Circle((76, 45), 1.6, fc="#333"))
    box(ax, 86, 41, 14, 8, "DRAFT", "mongo", 9, True)
    box(ax, 116, 41, 15, 8, "RECEIVED", "mongo", 9, True)
    arrow(ax, 77.6, 45, 86, 45, "tạo phiếu", toff=(0, 2))
    arrow(ax, 100, 45, 116, 45, "nhận hàng\n→ tạo lô", toff=(0, 3.5))
    ax.text(93, 33, "còn DRAFT: sửa hoặc xóa phiếu", ha="center", fontsize=7.5)
    ax.text(105, 4, "Nhận hàng: tạo lô + cộng tồn + RECEIVED trong một transaction.",
            ha="center", fontsize=7.5, style="italic")
    save(fig, "07_state_machine.png")


# 8. DFD mức 0 và mức 1
def dfd():
    fig, ax = canvas(15, 10)
    procs = [
        (20, 70, "1.0\nQuản lý tài khoản"), (55, 70, "2.0\nQuản lý danh mục"), (90, 70, "3.0\nQuản lý kho\n(nhập, lô, điều chỉnh)"),
        (20, 38, "4.0\nBán hàng\n(khuyến mãi, FEFO)"), (55, 38, "5.0\nTrả hàng,\nhoàn tiền"), (90, 38, "6.0\nChốt ca,\nthống kê"),
        (125, 54, "7.0\nTruy vết, gợi ý"),
    ]
    for x, y, t in procs:
        ax.add_patch(Ellipse((x, y), 24, 13, fc="#FFF3CD", ec="#333"))
        ax.text(x, y, t, ha="center", va="center", fontsize=8)
    stores = [
        (5, 88, "D1 users", "mongo"), (45, 88, "D2 products / categories / suppliers", "mongo"),
        (85, 88, "D3 batches / purchase_orders", "mongo"), (5, 15, "D4 orders / returns / promotions", "mongo"),
        (45, 15, "D5 stock_movements / payments_* (CQL)", "cass"), (85, 15, "D6 shift_closings / sync_queue", "mongo"),
        (113, 80, "D7 Graph: Order–Product–Batch–Supplier", "neo"),
        (113, 25, "D8 Redis: auth:*, cache:*, pos:held:*, top:sales:*", "redis"),
    ]
    for x, y, t, c in stores:
        ax.add_patch(Rectangle((x, y), 30, 6, fc=DB_COLORS[c], ec="#333"))
        ax.plot([x + 3, x + 3], [y, y + 6], color="#333", lw=0.8)
        ax.text(x + 16.5, y + 3, t, ha="center", va="center", fontsize=7)
    pairs = [((20, 76.5), (20, 88)), ((55, 76.5), (60, 88)), ((90, 76.5), (100, 88)),
             ((20, 31.5), (20, 21)), ((55, 31.5), (25, 21)), ((90, 31.5), (100, 21)),
             ((20, 31.5), (55, 21)), ((90, 63.5), (60, 21)), ((90, 31.5), (65, 21)),
             ((125, 60.5), (128, 80)), ((32, 38), (113, 28)), ((20, 63.5), (115, 31))]
    for (x1, y1), (x2, y2) in pairs:
        arrow(ax, x1, y1, x2, y2, style="<|-|>", color="#777")
    ax.text(75, 98, "DFD mức 1 (mức 0 là sơ đồ ngữ cảnh ở Hình 1)", ha="center", fontsize=11, fontweight="bold")
    legend(ax, 30, 3)
    save(fig, "08_dfd_muc_1.png")


# 9. Mô hình khái niệm
def er():
    fig, ax = canvas(15, 10)
    ents = {
        "Chốt ca": (5, 85), "Người dùng": (5, 62), "Đơn hàng": (5, 38),
        "Nhóm hàng": (40, 85), "Nhà cung cấp": (85, 85), "Phiếu nhập": (125, 85),
        "Sản phẩm": (40, 62), "Lô hàng": (85, 62), "Biến động kho": (125, 62),
        "Dòng đơn": (40, 38), "Cấp phát lô": (85, 38),
        "Khuyến mãi": (40, 14), "Thanh toán": (75, 14), "Phiếu trả": (110, 14),
    }
    for n, (x, y) in ents.items():
        box(ax, x, y, 20, 7, n, "plain", 9, True, rounded=False)
    def link(a, b, label):
        ax1, ay1 = ents[a]
        bx, by = ents[b]
        arrow(ax, ax1 + 10, ay1 + 3.5, bx + 10, by + 3.5, label, style="-", color="#555", fs=7)
    link("Nhóm hàng", "Sản phẩm", "1 – n")
    link("Nhà cung cấp", "Sản phẩm", "1 – n")
    link("Sản phẩm", "Lô hàng", "1 – n")
    link("Nhà cung cấp", "Lô hàng", "1 – n")
    link("Phiếu nhập", "Lô hàng", "1 – n")
    link("Nhà cung cấp", "Phiếu nhập", "1 – n")
    link("Người dùng", "Đơn hàng", "lập 1 – n")
    link("Đơn hàng", "Dòng đơn", "1 – n")
    link("Sản phẩm", "Dòng đơn", "1 – n")
    link("Dòng đơn", "Cấp phát lô", "1 – n")
    link("Lô hàng", "Cấp phát lô", "1 – n")
    link("Đơn hàng", "Thanh toán", "1 – n")
    link("Đơn hàng", "Phiếu trả", "1 – n")
    link("Khuyến mãi", "Dòng đơn", "áp 0..1")
    link("Lô hàng", "Biến động kho", "1 – n")
    link("Người dùng", "Chốt ca", "1 – n")
    ax.text(75, 98, "Mô hình khái niệm (chưa gắn với CSDL cụ thể)", ha="center", fontsize=11, fontweight="bold")
    save(fig, "09_mo_hinh_khai_niem.png")


if __name__ == "__main__":
    for f in (context, usecase, bpmn, activity, sequence, sequence_return, states, dfd, er):
        f()
        print("ok", f.__name__)
