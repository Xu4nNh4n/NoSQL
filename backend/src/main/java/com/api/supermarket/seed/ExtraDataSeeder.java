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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.catalog.product.ProductService;
import com.api.supermarket.catalog.supplier.Supplier;
import com.api.supermarket.catalog.supplier.SupplierService;
import com.api.supermarket.catalog.supplier.SupplierService.SupplierRequest;
import com.api.supermarket.common.AppTime;
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
import com.api.supermarket.user.UserRepository;

/**
 * Bổ sung dữ liệu vào CSDL đã có dữ liệu demo khi {@code APP_SEED_EXTRA=true}. Chỉ thêm, không sửa hay xóa.
 * Đi qua các service nghiệp vụ như {@link DemoDataSeeder} nên cả 4 CSDL cùng có. Chạy một lần: sản phẩm mốc
 * {@value #MARKER_SKU} đã tồn tại thì bỏ qua.
 */
@Component
@Order(21) // sau DemoDataSeeder
@ConditionalOnProperty(name = "app.seed.extra", havingValue = "true")
public class ExtraDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ExtraDataSeeder.class);
    private static final String MARKER_SKU = "TRUNG-GA-10";
    private static final int DAYS = 9;
    private static final int ORDERS = 110;

    private record P(String sku, String name, long price, String category, String supplier, int reorder, int quantity,
            Map<String, Object> attributes, Integer shelfDays) {
    }

    /** Nhập thêm sản phẩm cũ đã hết hàng hoặc chỉ còn lô quá hạn. */
    private record Restock(String sku, int quantity, Integer shelfDays) {
    }

    private static final String FRESH = "Thực phẩm tươi & đông lạnh";
    private static final String FRUIT = "Rau củ & trái cây";
    private static final String CARE = "Hóa mỹ phẩm";
    private static final String CP = "C.P. Việt Nam";
    private static final String DALAT = "Nông sản Đà Lạt";
    private static final String UNILEVER = "Unilever Việt Nam";
    private static final String MONDELEZ = "Mondelez Kinh Đô";

    private static final List<P> CATALOG = List.of(
            new P(MARKER_SKU, "Trứng gà C.P. hộp 10 quả", 32_000, FRESH, CP, 20, 120, Map.of("soLuong", "10 quả"), 14),
            new P("XUCXICH-CP", "Xúc xích C.P. gói 500g", 55_000, FRESH, CP, 15, 100, Map.of("khoiLuong", "500g", "baoQuan", "0-4°C"), 30),
            new P("HACAO-CP", "Há cảo tôm C.P. 300g", 62_000, FRESH, CP, 10, 60, Map.of("khoiLuong", "300g", "baoQuan", "-18°C"), 180),
            new P("UCGA-CP", "Ức gà phi lê 500g", 48_000, FRESH, CP, 10, 40, Map.of("khoiLuong", "500g", "baoQuan", "0-4°C"), 5),
            new P("CARROT-DL", "Cà rốt Đà Lạt 500g", 15_000, FRUIT, DALAT, 15, 70, Map.of("khoiLuong", "500g", "xuatXu", "Đà Lạt"), 7),
            new P("XALACH-DL", "Xà lách thủy canh 250g", 18_000, FRUIT, DALAT, 10, 50, Map.of("khoiLuong", "250g", "xuatXu", "Đà Lạt"), 4),
            new P("DAU-DL", "Dâu tây Đà Lạt hộp 250g", 85_000, FRUIT, DALAT, 8, 40, Map.of("khoiLuong", "250g", "xuatXu", "Đà Lạt"), 3),
            new P("CHUOI-LABA", "Chuối Laba nải 1kg", 28_000, FRUIT, DALAT, 10, 60, Map.of("khoiLuong", "1kg", "xuatXu", "Lâm Đồng"), 6),
            new P("OMO-3KG", "Bột giặt OMO 3kg", 145_000, CARE, UNILEVER, 8, 50, Map.of("khoiLuong", "3kg"), 900),
            new P("SUNLIGHT-750", "Nước rửa chén Sunlight 750g", 32_000, CARE, UNILEVER, 15, 90, Map.of("khoiLuong", "750g", "huong", "Chanh"), 900),
            new P("CLEAR-630", "Dầu gội Clear 630g", 168_000, CARE, UNILEVER, 8, 45, Map.of("khoiLuong", "630g", "huong", "Bạc hà"), 1000),
            new P("PS-230", "Kem đánh răng P/S 230g", 36_000, CARE, UNILEVER, 15, 100, Map.of("khoiLuong", "230g"), 900),
            new P("BANH-COSY", "Bánh quy Cosy 335g", 45_000, "Bánh kẹo", MONDELEZ, 12, 90, Map.of("khoiLuong", "335g"), 240),
            new P("SNACK-SLIDE", "Snack khoai tây Slide 100g", 24_000, "Bánh kẹo", MONDELEZ, 20, 160, Map.of("khoiLuong", "100g", "vi", "Phô mai"), 210),
            new P("BANH-AFC", "Bánh cracker AFC 172g", 27_000, "Bánh kẹo", MONDELEZ, 12, 90, Map.of("khoiLuong", "172g", "vi", "Lúa mì"), 240),
            new P("BONGLAN-SOLITE", "Bánh bông lan Solite 360g", 52_000, "Bánh kẹo", MONDELEZ, 10, 70, Map.of("khoiLuong", "360g"), 25));

    private static final Map<String, List<Restock>> RESTOCK = Map.of(
            "Masan Consumer", List.of(new Restock("GAO-ST25", 40, 300)),
            "Vinamilk", List.of(new Restock("SUA-VNM-1L", 120, 12), new Restock("SUACHUA-VNM", 160, 20)),
            "TH True Milk", List.of(new Restock("SUA-TH-180", 120, 60)),
            "Điện Quang", List.of(new Restock("NOI-COM-1L8", 12, null)));

    /** Nhóm hàng hay mua cùng nhau, để gợi ý mua kèm trên Neo4j có ý nghĩa. */
    private static final List<List<String>> BUNDLES = List.of(
            List.of("MI-HAOHAO", MARKER_SKU, "XUCXICH-CP"),
            List.of("BIA-TIGER", "SNACK-SLIDE", "XUCXICH-CP"),
            List.of("SUA-VNM-1L", "BANH-COSY", "CHUOI-LABA"),
            List.of("UCGA-CP", "XALACH-DL", "CARROT-DL"),
            List.of("OMO-3KG", "SUNLIGHT-750", "PS-230"),
            List.of("CLEAR-630", "PS-230"),
            List.of("COCA-330", "SNACK-SLIDE", "BANH-OREO"),
            List.of("GAO-ST25", "NUOCMAM-NC", MARKER_SKU),
            List.of("SUACHUA-VNM", "DAU-DL", "CHUOI-LABA"),
            List.of("SUA-TH-180", "BONGLAN-SOLITE"),
            List.of("HACAO-CP", "NUOCMAM-NC"),
            List.of("BANH-AFC", "NUOC-AQUA"));

    private final MongoTemplate mongo;
    private final UserRepository users;
    private final CategoryService categories;
    private final SupplierService suppliers;
    private final ProductService products;
    private final ProductRepository productRepository;
    private final PurchaseOrderService purchaseOrders;
    private final PromotionService promotions;
    private final QuoteService quotes;
    private final OrderService orders;
    private final ReturnService returns;
    private final AppTime time;

    public ExtraDataSeeder(MongoTemplate mongo, UserRepository users, CategoryService categories, SupplierService suppliers,
            ProductService products, ProductRepository productRepository, PurchaseOrderService purchaseOrders,
            PromotionService promotions, QuoteService quotes, OrderService orders, ReturnService returns, AppTime time) {
        this.mongo = mongo;
        this.users = users;
        this.categories = categories;
        this.suppliers = suppliers;
        this.products = products;
        this.productRepository = productRepository;
        this.purchaseOrders = purchaseOrders;
        this.promotions = promotions;
        this.quotes = quotes;
        this.orders = orders;
        this.returns = returns;
        this.time = time;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (productRepository.findBySku(MARKER_SKU).isPresent()) {
                log.info("Đã có dữ liệu bổ sung, bỏ qua");
                return;
            }
            if (productRepository.findBySku("MI-HAOHAO").isEmpty()) {
                log.warn("Chưa có dữ liệu demo gốc (APP_SEED_DEMO), bỏ qua dữ liệu bổ sung");
                return;
            }
            seed();
        } catch (RuntimeException ex) {
            log.error("Bổ sung dữ liệu thất bại", ex);
        }
    }

    private void seed() {
        JwtPrincipal keeper = account("thukho01");
        List<JwtPrincipal> cashiers = List.of(account("thungan01"), account("thungan02"));

        Map<String, String> cat = new LinkedHashMap<>();
        categories.list(null).forEach(c -> cat.put(c.getName(), c.getId()));
        for (String[] c : new String[][] { { FRESH, "Trứng, thịt, đồ đông lạnh" }, { FRUIT, "Rau củ, trái cây tươi" },
                { CARE, "Giặt rửa, chăm sóc cá nhân" } }) {
            if (!cat.containsKey(c[0])) {
                Category saved = categories.create(new CategoryRequest(c[0], c[1], true));
                cat.put(c[0], saved.getId());
            }
        }
        Map<String, String> sup = new LinkedHashMap<>();
        mongo.findAll(Supplier.class).forEach(s -> sup.put(s.getName(), s.getId()));
        for (String[] s : new String[][] { { CP, "0281111007", "Đồng Nai" }, { DALAT, "0281111008", "Lâm Đồng" },
                { UNILEVER, "0281111009", "TP. Hồ Chí Minh" }, { MONDELEZ, "0281111010", "Bình Dương" } }) {
            if (!sup.containsKey(s[0])) {
                sup.put(s[0], suppliers.create(new SupplierRequest(s[0], s[1], null, s[2], true)).getId());
            }
        }

        Map<String, Product> bySku = new LinkedHashMap<>();
        mongo.findAll(Product.class).forEach(p -> bySku.put(p.getSku(), p));
        for (P p : CATALOG) {
            if (!bySku.containsKey(p.sku())) {
                String id = products.create(new ProductRequest(p.sku(), p.name(), BigDecimal.valueOf(p.price()),
                        cat.get(p.category()), sup.get(p.supplier()), p.reorder(), null, p.attributes(), true, null, null,
                        null)).id();
                bySku.put(p.sku(), products.load(id));
            }
        }

        for (String s : List.of(CP, DALAT, UNILEVER, MONDELEZ)) {
            receive(sup.get(s), "Nhập hàng bổ sung", CATALOG.stream().filter(p -> p.supplier().equals(s))
                    .map(p -> new Restock(p.sku(), p.quantity(), p.shelfDays())).toList(), bySku, keeper);
        }
        RESTOCK.forEach((s, lines) -> {
            if (sup.containsKey(s)) {
                receive(sup.get(s), "Nhập bù hàng hết", lines, bySku, keeper);
            }
        });
        // Một phiếu để nháp, để thử thao tác nhận hàng trên giao diện
        purchaseOrders.create(new PurchaseOrderRequest(sup.get(DALAT), "Đặt rau quả tuần tới, chờ nhận",
                List.of(line(bySku.get("XALACH-DL"), 40), line(bySku.get("DAU-DL"), 30)), null), keeper);

        Instant starts = time.now().minus(Duration.ofDays(DAYS));
        Instant ends = time.now().plus(Duration.ofDays(30));
        promotions.create(new PromotionRequest("Hóa mỹ phẩm giảm 15%", Promotion.Type.PERCENT, starts, ends, null,
                List.of(cat.get(CARE)), BigDecimal.valueOf(15), null, null, null, null, null, null, true, null));
        promotions.create(new PromotionRequest("Snack Slide mua 2 tặng 1", Promotion.Type.BUY_X_GET_Y, starts, ends,
                List.of(bySku.get("SNACK-SLIDE").getId()), null, null, null, 2, 1, null, null, null, true, null));
        promotions.create(new PromotionRequest("Trứng gà giảm 3.000đ", Promotion.Type.FIXED, starts, ends,
                List.of(bySku.get(MARKER_SKU).getId()), null, null, BigDecimal.valueOf(3000), null, null, null, null,
                null, true, null));

        List<SalesOrder> todayOrders = new ArrayList<>();
        int created = sell(bySku, cashiers, todayOrders);
        int returned = returnSome(todayOrders);
        log.info("Dữ liệu bổ sung: {} sản phẩm mới, {} đơn, {} phiếu trả", CATALOG.size(), created, returned);
    }

    private void receive(String supplierId, String note, List<Restock> lines, Map<String, Product> bySku,
            JwtPrincipal keeper) {
        List<Restock> known = lines.stream().filter(r -> bySku.containsKey(r.sku())).toList();
        if (known.isEmpty()) {
            return;
        }
        PurchaseOrder po = purchaseOrders.create(new PurchaseOrderRequest(supplierId, note,
                known.stream().map(r -> line(bySku.get(r.sku()), r.quantity())).toList(), null), keeper);
        LocalDate today = time.today();
        List<ReceiveLine> receive = new ArrayList<>();
        for (int i = 0; i < known.size(); i++) {
            Restock r = known.get(i);
            boolean dated = r.shelfDays() != null;
            receive.add(new ReceiveLine(i + 1, r.quantity(), dated ? today.minusDays(1) : null,
                    dated ? today.plusDays(r.shelfDays()) : null));
        }
        purchaseOrders.receive(po.getId(), new ReceiveRequest(receive), keeper);
    }

    /** Đơn rải đều các ngày gần nhất, 8h–21h, random cố định. Đơn thiếu hàng thì bỏ qua. */
    private int sell(Map<String, Product> bySku, List<JwtPrincipal> cashiers, List<SalesOrder> todayOrders) {
        Random rnd = new Random(20261124);
        List<Product> all = bySku.values().stream().filter(p -> p.isActive() && !p.getSku().equals("LY-TIGER")).toList();
        LocalDate today = time.today();
        int created = 0;
        for (int i = 0; i < ORDERS; i++) {
            int daysAgo = i % DAYS;
            Instant at = today.minusDays(daysAgo).atStartOfDay(time.zone()).toInstant()
                    .plus(Duration.ofMinutes(8 * 60 + rnd.nextInt(13 * 60)));
            if (at.isAfter(time.now())) {
                at = time.now().minus(Duration.ofMinutes(rnd.nextInt(60) + 1));
            }
            Map<String, Integer> cart = new LinkedHashMap<>();
            for (String sku : BUNDLES.get(rnd.nextInt(BUNDLES.size()))) {
                if (rnd.nextInt(10) < 8 && bySku.containsKey(sku)) {
                    cart.merge(bySku.get(sku).getId(), quantity(sku, rnd), Integer::sum);
                }
            }
            if (cart.isEmpty() || rnd.nextInt(10) < 4) {
                Product p = all.get(rnd.nextInt(all.size()));
                cart.merge(p.getId(), quantity(p.getSku(), rnd), Integer::sum);
            }
            List<QuoteItem> items = cart.entrySet().stream().map(e -> new QuoteItem(e.getKey(), e.getValue())).toList();
            boolean bank = rnd.nextInt(10) < 4;
            String ref = bank ? "FT" + (100000 + rnd.nextInt(900000)) : null;
            JwtPrincipal cashier = cashiers.get(rnd.nextInt(cashiers.size()));
            try {
                SalesOrder o = orders.create(new CreateOrderRequest("seed-extra-" + i, items,
                        bank ? SalesOrder.Method.BANK_TRANSFER : SalesOrder.Method.CASH, ref,
                        quotes.quote(items).quoteHash()), cashier, at);
                created++;
                if (daysAgo == 0) {
                    todayOrders.add(o);
                }
            } catch (BadRequestException ex) {
                log.debug("Bỏ qua một đơn bổ sung: {}", ex.getMessage());
            }
        }
        return created;
    }

    private int returnSome(List<SalesOrder> todayOrders) {
        int returned = 0;
        for (int i = 0; i < Math.min(3, todayOrders.size()); i++) {
            SalesOrder o = todayOrders.get(i);
            JwtPrincipal cashier = new JwtPrincipal(o.getCashier().id(), o.getCashier().name(), "STAFF");
            try {
                returns.create(o.getId(), new ReturnRequest("seed-extra-return-" + i,
                        List.of(new ReturnItemRequest(o.getItems().get(0).getLineNo(), 1, i != 0,
                                i == 0 ? "Bao bì rách" : "Khách đổi ý")),
                        SalesOrder.Method.CASH, null), cashier);
                returned++;
            } catch (BadRequestException ex) {
                log.debug("Bỏ qua một phiếu trả bổ sung: {}", ex.getMessage());
            }
        }
        return returned;
    }

    private static LineRequest line(Product p, int quantity) {
        BigDecimal cost = p.getPrice().multiply(BigDecimal.valueOf(7)).divideToIntegralValue(BigDecimal.TEN);
        return new LineRequest(p.getId(), quantity, cost);
    }

    private static int quantity(String sku, Random rnd) {
        return switch (sku) {
            case "BIA-TIGER" -> 6;
            case "MI-HAOHAO" -> 2 + rnd.nextInt(4);
            case "GAO-ST25", "NOI-COM-1L8", "OMO-3KG", "CLEAR-630" -> 1;
            default -> 1 + rnd.nextInt(3);
        };
    }

    private JwtPrincipal account(String username) {
        return users.findByUsername(username)
                .map(u -> new JwtPrincipal(u.getId(), u.getUsername(), u.getRole().name()))
                .orElseThrow(() -> new IllegalStateException("Thiếu tài khoản demo " + username));
    }
}
