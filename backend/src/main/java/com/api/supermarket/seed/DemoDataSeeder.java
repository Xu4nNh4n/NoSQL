package com.api.supermarket.seed;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.api.supermarket.catalog.category.Category;
import com.api.supermarket.catalog.category.CategoryService;
import com.api.supermarket.catalog.category.CategoryService.CategoryRequest;
import com.api.supermarket.catalog.product.Product;
import com.api.supermarket.catalog.product.ProductDtos.ProductRequest;
import com.api.supermarket.catalog.product.ProductService;
import com.api.supermarket.catalog.supplier.Supplier;
import com.api.supermarket.catalog.supplier.SupplierService;
import com.api.supermarket.catalog.supplier.SupplierService.SupplierRequest;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.customer.Customer;
import com.api.supermarket.customer.CustomerService;
import com.api.supermarket.customer.CustomerService.CustomerRequest;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.inventory.PurchaseOrder;
import com.api.supermarket.inventory.PurchaseOrderService;
import com.api.supermarket.inventory.PurchaseOrderService.LineRequest;
import com.api.supermarket.inventory.PurchaseOrderService.PurchaseOrderRequest;
import com.api.supermarket.inventory.PurchaseOrderService.ReceiveLine;
import com.api.supermarket.inventory.PurchaseOrderService.ReceiveRequest;
import com.api.supermarket.promotion.Promotion;
import com.api.supermarket.promotion.PromotionService;
import com.api.supermarket.promotion.PromotionService.PromotionRequest;
import com.api.supermarket.sales.OrderService;
import com.api.supermarket.sales.OrderService.CreateOrderRequest;
import com.api.supermarket.sales.QuoteService;
import com.api.supermarket.sales.QuoteService.QuoteItem;
import com.api.supermarket.sales.ReturnService;
import com.api.supermarket.sales.ReturnService.ReturnItemRequest;
import com.api.supermarket.sales.ReturnService.ReturnRequest;
import com.api.supermarket.sales.SalesOrder;
import com.api.supermarket.security.JwtPrincipal;
import com.api.supermarket.user.Role;
import com.api.supermarket.user.UserRepository;
import com.api.supermarket.user.UserService;
import com.api.supermarket.user.dto.CreateUserRequest;
import com.api.supermarket.user.dto.UserResponse;

/**
 * Tạo dữ liệu demo khi {@code APP_SEED_DEMO=true} và chưa có sản phẩm nào. Đi qua đúng các service nghiệp vụ
 * (phiếu nhập → nhận lô → bán → trả), nên Cassandra (thẻ kho, thanh toán), Neo4j (đồ thị) và Redis (top bán)
 * đều có dữ liệu thật qua sync_queue. Mật khẩu các tài khoản demo lấy từ {@code APP_SEED_PASSWORD}.
 */
@Component
@Order(20)
@ConditionalOnProperty(name = "app.seed.demo", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private record P(String sku, String name, long price, String category, String supplier, int reorder,
            Map<String, Object> attributes, Integer shelfDays) {
    }

    private final MongoTemplate mongo;
    private final UserService users;
    private final UserRepository userRepository;
    private final CategoryService categories;
    private final SupplierService suppliers;
    private final ProductService products;
    private final CustomerService customers;
    private final PurchaseOrderService purchaseOrders;
    private final PromotionService promotions;
    private final QuoteService quotes;
    private final OrderService orders;
    private final ReturnService returns;
    private final AppTime time;
    private final String password;
    private final int orderCount;

    public DemoDataSeeder(MongoTemplate mongo, UserService users, UserRepository userRepository, CategoryService categories,
            SupplierService suppliers, ProductService products, CustomerService customers,
            PurchaseOrderService purchaseOrders, PromotionService promotions, QuoteService quotes, OrderService orders,
            ReturnService returns, AppTime time,
            @Value("${app.seed.password:}") String password,
            @Value("${app.bootstrap.admin-password:}") String adminPassword,
            @Value("${app.seed.orders:80}") int orderCount) {
        this.mongo = mongo;
        this.users = users;
        this.userRepository = userRepository;
        this.categories = categories;
        this.suppliers = suppliers;
        this.products = products;
        this.customers = customers;
        this.purchaseOrders = purchaseOrders;
        this.promotions = promotions;
        this.quotes = quotes;
        this.orders = orders;
        this.returns = returns;
        this.time = time;
        // APP_SEED_PASSWORD để trống trong .env là chuỗi rỗng, không phải "không có": dùng mật khẩu admin
        this.password = password == null || password.isBlank() ? adminPassword : password;
        this.orderCount = orderCount;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (mongo.estimatedCount(Product.class) > 0) {
                log.info("Đã có dữ liệu, bỏ qua tạo dữ liệu demo");
                return;
            }
            if (password == null || password.length() < 8) {
                log.warn("Thiếu APP_SEED_PASSWORD (hoặc APP_ADMIN_PASSWORD) ≥ 8 ký tự, bỏ qua tạo dữ liệu demo");
                return;
            }
            long start = System.nanoTime();
            seed();
            log.info("Tạo dữ liệu demo xong sau {} giây", (System.nanoTime() - start) / 1_000_000_000);
        } catch (RuntimeException ex) {
            log.error("Tạo dữ liệu demo thất bại", ex);
        }
    }

    private void seed() {
        JwtPrincipal admin = account("admin", "Quản trị viên", Role.ADMIN);
        JwtPrincipal keeper = account("thukho01", "Thủ kho Minh", Role.WAREHOUSE);
        List<JwtPrincipal> cashiers = List.of(account("thungan01", "Thu ngân Lan", Role.STAFF),
                account("thungan02", "Thu ngân Hùng", Role.STAFF));

        Map<String, String> cat = new LinkedHashMap<>();
        for (String[] c : new String[][] { { "Sữa & chế phẩm", "Sữa tươi, sữa chua, sữa hộp" },
                { "Đồ uống", "Nước ngọt, bia, nước suối" }, { "Mì & đồ khô", "Mì gói, gạo, gia vị" },
                { "Bánh kẹo", "Bánh quy, kẹo, snack" }, { "Gia dụng", "Đồ điện, đồ dùng nhà bếp" } }) {
            Category saved = categories.create(new CategoryRequest(c[0], c[1], true));
            cat.put(c[0], saved.getId());
        }
        Map<String, String> sup = new LinkedHashMap<>();
        for (String[] s : new String[][] { { "Vinamilk", "0281111001" }, { "TH True Milk", "0281111002" },
                { "Sabeco - Tiger Beer", "0281111003" }, { "Acecook Việt Nam", "0281111004" },
                { "Masan Consumer", "0281111005" }, { "Điện Quang", "0281111006" } }) {
            Supplier saved = suppliers.create(new SupplierRequest(s[0], s[1], null, "TP. Hồ Chí Minh", true));
            sup.put(s[0], saved.getId());
        }

        List<P> catalog = List.of(
                new P("SUA-VNM-1L", "Sữa tươi Vinamilk 1L", 32_000, "Sữa & chế phẩm", "Vinamilk", 20, Map.of("dungTich", "1L", "loai", "Không đường"), 12),
                new P("SUA-TH-180", "Sữa tươi TH 180ml (lốc 4)", 36_000, "Sữa & chế phẩm", "TH True Milk", 15, Map.of("dungTich", "180ml", "soHop", 4), 2),
                new P("SUACHUA-VNM", "Sữa chua Vinamilk có đường", 7_000, "Sữa & chế phẩm", "Vinamilk", 30, Map.of("khoiLuong", "100g"), 3),
                new P("BIA-TIGER", "Bia Tiger lon 330ml", 18_000, "Đồ uống", "Sabeco - Tiger Beer", 48, Map.of("dungTich", "330ml", "nongDo", "5%"), 180),
                new P("LY-TIGER", "Ly thủy tinh Tiger (quà tặng)", 25_000, "Gia dụng", "Sabeco - Tiger Beer", 5, Map.of("chatLieu", "Thủy tinh"), null),
                new P("NUOC-AQUA", "Nước suối Aquafina 500ml", 6_000, "Đồ uống", "Masan Consumer", 40, Map.of("dungTich", "500ml"), 365),
                new P("COCA-330", "Coca-Cola lon 330ml", 10_000, "Đồ uống", "Masan Consumer", 40, Map.of("dungTich", "330ml"), 270),
                new P("MI-HAOHAO", "Mì Hảo Hảo tôm chua cay", 4_500, "Mì & đồ khô", "Acecook Việt Nam", 60, Map.of("khoiLuong", "75g"), 150),
                new P("MI-OMACHI", "Mì Omachi sườn hầm", 8_000, "Mì & đồ khô", "Masan Consumer", 40, Map.of("khoiLuong", "80g"), 150),
                new P("GAO-ST25", "Gạo ST25 túi 5kg", 185_000, "Mì & đồ khô", "Masan Consumer", 10, Map.of("khoiLuong", "5kg", "xuatXu", "Sóc Trăng"), 300),
                new P("NUOCMAM-NC", "Nước mắm Nam Ngư 500ml", 38_000, "Mì & đồ khô", "Masan Consumer", 12, Map.of("dungTich", "500ml"), 540),
                new P("BANH-OREO", "Bánh Oreo vani 133g", 21_000, "Bánh kẹo", "Masan Consumer", 20, Map.of("khoiLuong", "133g"), 200),
                new P("KEO-ALPEN", "Kẹo Alpenliebe gói 120g", 15_000, "Bánh kẹo", "Masan Consumer", 20, Map.of("khoiLuong", "120g"), 360),
                new P("DEN-LED-9W", "Bóng đèn LED Điện Quang 9W", 35_000, "Gia dụng", "Điện Quang", 10, Map.of("congSuat", "9W", "baoHanhThang", 12), null),
                new P("NOI-COM-1L8", "Nồi cơm điện 1,8L", 690_000, "Gia dụng", "Điện Quang", 3, Map.of("dungTich", "1,8L", "baoHanhThang", 24, "congSuat", "700W"), null));

        Map<String, Product> bySku = new LinkedHashMap<>();
        for (P p : catalog) {
            String id = products.create(new ProductRequest(p.sku(), p.name(), BigDecimal.valueOf(p.price()), cat.get(p.category()),
                    sup.get(p.supplier()), p.reorder(), null, p.attributes(), true, null, null, null)).id();
            bySku.put(p.sku(), products.load(id));
        }

        // Nhập hàng bằng phiếu nhập theo từng nhà cung cấp (DRAFT → RECEIVED)
        LocalDate today = time.today();
        for (Map.Entry<String, String> s : sup.entrySet()) {
            List<P> lines = catalog.stream().filter(p -> p.supplier().equals(s.getKey())).toList();
            if (lines.isEmpty()) {
                continue;
            }
            PurchaseOrder po = purchaseOrders.create(new PurchaseOrderRequest(s.getValue(), "Nhập hàng đầu kỳ",
                    lines.stream().map(p -> new LineRequest(bySku.get(p.sku()).getId(), quantityFor(p),
                            BigDecimal.valueOf(p.price() * 7 / 10))).toList(), null), keeper);
            List<ReceiveLine> receive = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                P p = lines.get(i);
                LocalDate expiry = p.shelfDays() == null ? null : today.plusDays(p.shelfDays());
                receive.add(new ReceiveLine(i + 1, quantityFor(p), p.shelfDays() == null ? null : today.minusDays(2), expiry));
            }
            purchaseOrders.receive(po.getId(), new ReceiveRequest(receive), keeper);
        }

        Instant starts = time.now().minus(Duration.ofDays(10));
        Instant ends = time.now().plus(Duration.ofDays(30));
        promotions.create(new PromotionRequest("Sữa giảm 10%", Promotion.Type.PERCENT, starts, ends, null,
                List.of(cat.get("Sữa & chế phẩm")), BigDecimal.TEN, null, null, null, null, null, null, true, null));
        promotions.create(new PromotionRequest("Mì Hảo Hảo giảm 1.000đ", Promotion.Type.FIXED, starts, ends,
                List.of(bySku.get("MI-HAOHAO").getId()), null, null, BigDecimal.valueOf(1000), null, null, null, null, null, true, null));
        promotions.create(new PromotionRequest("Sữa chua mua 4 tặng 1", Promotion.Type.BUY_X_GET_Y, starts, ends,
                List.of(bySku.get("SUACHUA-VNM").getId()), null, null, null, 4, 1, null, null, null, true, null));
        promotions.create(new PromotionRequest("Mua 6 lon Tiger tặng 1 ly", Promotion.Type.BUY_X_GET_Y, starts, ends,
                List.of(bySku.get("BIA-TIGER").getId()), null, null, null, 6, 1, bySku.get("LY-TIGER").getId(), null, null, true, null));
        promotions.create(new PromotionRequest("Đơn từ 300k giảm 20k", Promotion.Type.ORDER_THRESHOLD, starts, ends, null, null,
                null, BigDecimal.valueOf(20_000), null, null, null, BigDecimal.valueOf(300_000), null, true, null));

        List<Customer> people = new ArrayList<>();
        String[][] names = { { "Nguyễn Thị Bình", "0912000001" }, { "Trần Văn Cường", "0912000002" },
                { "Lê Thị Dung", "0912000003" }, { "Phạm Minh Đức", "0912000004" }, { "Hoàng Thu Hà", "0912000005" },
                { "Võ Quốc Khánh", "0912000006" }, { "Đặng Mỹ Linh", "0912000007" }, { "Bùi Gia Nam", "0912000008" } };
        for (String[] n : names) {
            people.add(customers.create(new CustomerRequest(n[0], n[1], null, "TP. Hồ Chí Minh", true, null)));
        }

        // Đơn bán rải đều 7 ngày gần nhất, 8h–21h. Random cố định để lần nào tạo cũng giống nhau.
        Random rnd = new Random(20261107);
        List<Product> sellable = bySku.values().stream().filter(p -> !p.getSku().equals("LY-TIGER")).toList();
        List<SalesOrder> todayOrders = new ArrayList<>();
        int created = 0;
        for (int i = 0; i < orderCount; i++) {
            int daysAgo = rnd.nextInt(7);
            Instant at = today.minusDays(daysAgo).atStartOfDay(time.zone()).toInstant()
                    .plus(Duration.ofMinutes(8 * 60 + rnd.nextInt(13 * 60)));
            if (at.isAfter(time.now())) {
                at = time.now().minus(Duration.ofMinutes(rnd.nextInt(60) + 1));
            }
            Map<String, Integer> cart = new LinkedHashMap<>();
            int lines = 1 + rnd.nextInt(4);
            for (int l = 0; l < lines; l++) {
                Product p = sellable.get(rnd.nextInt(sellable.size()));
                int qty = p.getSku().equals("BIA-TIGER") ? 6 : 1 + rnd.nextInt(3);
                cart.merge(p.getId(), qty, Integer::sum);
            }
            List<QuoteItem> items = cart.entrySet().stream().map(e -> new QuoteItem(e.getKey(), e.getValue())).toList();
            boolean bank = rnd.nextInt(10) < 3;
            String customerId = rnd.nextBoolean() ? people.get(rnd.nextInt(people.size())).getId() : null;
            JwtPrincipal cashier = cashiers.get(rnd.nextInt(cashiers.size()));
            try {
                String hash = quotes.quote(items).quoteHash();
                SalesOrder o = orders.create(new CreateOrderRequest(UUID.randomUUID().toString(), customerId, items,
                        bank ? SalesOrder.Method.BANK_TRANSFER : SalesOrder.Method.CASH,
                        bank ? "FT" + (100000 + rnd.nextInt(900000)) : null, hash), cashier, at);
                created++;
                if (daysAgo == 0) {
                    todayOrders.add(o);
                }
            } catch (BadRequestException ex) {
                log.debug("Bỏ qua một đơn demo: {}", ex.getMessage());
            }
        }
        for (int i = 0; i < Math.min(3, todayOrders.size()); i++) {
            SalesOrder o = todayOrders.get(i);
            JwtPrincipal cashier = new JwtPrincipal(o.getCashier().id(), o.getCashier().name(), "STAFF");
            returns.create(o.getId(), new ReturnRequest(UUID.randomUUID().toString(),
                    List.of(new ReturnItemRequest(o.getItems().get(0).getLineNo(), 1, i != 2, i == 2 ? "Hộp móp" : "Khách đổi ý")),
                    SalesOrder.Method.CASH, null), cashier);
        }
        log.info("Dữ liệu demo: {} sản phẩm, {} khách, {} đơn, {} phiếu trả", bySku.size(), people.size(), created,
                Math.min(3, todayOrders.size()));
    }

    private static int quantityFor(P p) {
        return p.price() > 100_000 ? 15 : p.sku().startsWith("BIA") ? 400 : 150;
    }

    private JwtPrincipal account(String username, String fullName, Role role) {
        UserResponse u = userRepository.findByUsername(username).map(UserResponse::from)
                .orElseGet(() -> users.create(new CreateUserRequest(username, password, fullName, null, null, role, true)));
        return new JwtPrincipal(u.id(), u.username(), u.role().name());
    }
}
