package com.api.supermarket.sales;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.api.supermarket.catalog.product.ProductCache;
import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.CodeGenerator;
import com.api.supermarket.common.ConflictException;
import com.api.supermarket.common.DocumentType;
import com.api.supermarket.common.MongoTx;
import com.api.supermarket.customer.Customer;
import com.api.supermarket.customer.CustomerService;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.inventory.FefoAllocator;
import com.api.supermarket.inventory.InventoryEvents;
import com.api.supermarket.promotion.Pricing.PricedCart;
import com.api.supermarket.promotion.Pricing.PricedLine;
import com.api.supermarket.sales.QuoteService.QuoteItem;
import com.api.supermarket.security.JwtPrincipal;
import com.api.supermarket.sync.SyncQueue;
import com.api.supermarket.user.UserFields;
import com.api.supermarket.validation.PaginationValidator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Bán tại quầy (FR-07). Một transaction MongoDB: kiểm tra requestKey, tính lại giá, so quoteHash, cấp phát FEFO
 * cho mọi dòng (kể cả quà), trừ tồn, tạo đơn có thanh toán nhúng và xếp sự kiện ORDER_CREATED.
 */
@Service
public class OrderService {
    public static final Set<String> SORT_FIELDS = Set.of("orderedAt", "total", "orderCode");

    public record CreateOrderRequest(
            @NotBlank @Size(max = 64) String requestKey,
            String customerId,
            @NotEmpty @Size(max = 100) List<@Valid QuoteItem> items,
            @NotNull SalesOrder.Method paymentMethod,
            @Size(max = 100) String transactionRef,
            @NotBlank String quoteHash) {
    }

    private final OrderRepository orders;
    private final QuoteService quotes;
    private final FefoAllocator fefo;
    private final ProductRepository products;
    private final CustomerService customers;
    private final CodeGenerator codes;
    private final SyncQueue sync;
    private final MongoTx tx;
    private final ProductCache cache;
    private final TopSalesService topSales;
    private final AppTime time;

    public OrderService(OrderRepository orders, QuoteService quotes, FefoAllocator fefo, ProductRepository products,
            CustomerService customers, CodeGenerator codes, SyncQueue sync, MongoTx tx, ProductCache cache,
            TopSalesService topSales, AppTime time) {
        this.orders = orders;
        this.quotes = quotes;
        this.fefo = fefo;
        this.products = products;
        this.customers = customers;
        this.codes = codes;
        this.sync = sync;
        this.tx = tx;
        this.cache = cache;
        this.topSales = topSales;
        this.time = time;
    }

    public SalesOrder load(String id) {
        return orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));
    }

    public SalesOrder byCode(String code) {
        return orders.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn " + code));
    }

    public PageResponse<SalesOrder> list(LocalDate from, LocalDate to, SalesOrder.Status status, SalesOrder.Method method,
            String cashierId, int page, int size, String sortBy, String sortDir) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("Từ ngày phải trước hoặc bằng đến ngày");
        }
        Instant fromAt = from == null ? null : from.atStartOfDay(time.zone()).toInstant();
        Instant toAt = to == null ? null : to.plusDays(1).atStartOfDay(time.zone()).toInstant();
        return PageResponse.of(orders.search(fromAt, toAt, status, method, cashierId,
                PaginationValidator.pageRequest(page, size, sortBy, sortDir, SORT_FIELDS)));
    }

    public SalesOrder create(CreateOrderRequest req, JwtPrincipal actor) {
        return create(req, actor, time.now());
    }

    /**
     * Tạo đơn với thời điểm bán cho trước. Chỉ dùng cho bộ tạo dữ liệu mẫu (đơn của 30 ngày trước để trang thống kê
     * và chốt ca có dữ liệu); API luôn dùng thời điểm hiện tại.
     */
    public SalesOrder create(CreateOrderRequest req, JwtPrincipal actor, Instant orderedAt) {
        String ref = UserFields.text(req.transactionRef());
        if (req.paymentMethod() == SalesOrder.Method.BANK_TRANSFER && ref == null) {
            throw new BadRequestException("Chuyển khoản phải nhập mã giao dịch");
        }
        String fingerprint = fingerprint(req, ref);
        SalesOrder existing = orders.findByRequestKey(req.requestKey()).orElse(null);
        if (existing != null) {
            return sameRequest(existing, fingerprint);
        }
        SalesOrder order;
        try {
            order = tx.run(() -> createInTx(req, ref, fingerprint, actor, orderedAt));
        } catch (DuplicateKeyException ex) {
            // Hai yêu cầu cùng requestKey chạy song song: index unique chặn bản thứ hai.
            existing = orders.findByRequestKey(req.requestKey()).orElseThrow(() -> ex);
            return sameRequest(existing, fingerprint);
        }
        topSales.record(order);
        cache.evict();
        return order;
    }

    private SalesOrder createInTx(CreateOrderRequest req, String ref, String fingerprint, JwtPrincipal actor,
            Instant now) {
        PricedCart cart = quotes.quote(req.items());
        if (!cart.quoteHash().equals(req.quoteHash())) {
            throw new ConflictException("PRICE_CHANGED", "Giá hoặc khuyến mãi đã thay đổi, vui lòng tính lại giỏ hàng");
        }
        Customer customer = req.customerId() == null || req.customerId().isBlank() ? null
                : customers.loadActive(req.customerId());

        SalesOrder order = new SalesOrder();
        order.setOrderCode(codes.next(DocumentType.ORDER));
        order.setRequestKey(req.requestKey());
        order.setRequestFingerprint(fingerprint);
        order.setCashier(new SalesOrder.PersonRef(actor.userId(), actor.username(), null));
        order.setCustomer(customer == null ? null
                : new SalesOrder.PersonRef(customer.getId(), customer.getFullName(), customer.getPhone()));
        order.setOrderedAt(now);
        order.setOrderDay(now.atZone(time.zone()).toLocalDate());
        order.setStatus(SalesOrder.Status.PAID);
        order.setPaymentMethod(req.paymentMethod());
        order.setTransactionRef(ref);
        order.setGrossTotal(cart.grossTotal());
        order.setDiscountTotal(cart.discountTotal());
        order.setTotal(cart.total());
        order.setOrderPromotion(cart.orderPromotion());

        for (PricedLine pl : cart.lines()) {
            SalesOrder.OrderLine line = new SalesOrder.OrderLine();
            line.setLineNo(pl.lineNo());
            line.setProductId(pl.productId());
            line.setSku(pl.sku());
            line.setName(pl.name());
            line.setCategoryId(pl.categoryId());
            line.setQuantity(pl.quantity());
            line.setUnitPrice(pl.unitPrice());
            line.setGrossAmount(pl.grossAmount());
            line.setNetAmount(pl.netAmount());
            line.setGift(pl.gift());
            line.setPromotion(pl.promotion());
            for (FefoAllocator.Allocation a : fefo.allocate(pl.productId(), pl.name(), pl.quantity())) {
                SalesOrder.Allocation alloc = new SalesOrder.Allocation();
                alloc.setBatchId(a.batchId());
                alloc.setBatchCode(a.batchCode());
                alloc.setExpiryDate(a.expiryDate());
                alloc.setQuantity(a.quantity());
                line.getAllocations().add(alloc);
            }
            products.incStock(pl.productId(), -pl.quantity(), 0);
            order.getItems().add(line);
        }

        SalesOrder.Payment payment = new SalesOrder.Payment();
        payment.setPaymentId("PAY-" + order.getOrderCode());
        payment.setType(SalesOrder.Payment.Type.PAYMENT);
        payment.setMethod(req.paymentMethod());
        payment.setAmount(cart.total());
        payment.setReference(ref != null ? ref : "POS-" + order.getOrderCode());
        payment.setAt(now);
        payment.setBy(actor.username());
        order.getPayments().add(payment);

        SalesOrder saved = orders.insert(order);
        sync.enqueue(InventoryEvents.ORDER_CREATED, saved.getId(), SalesEvents.orderCreated(saved), actor.userId());
        return saved;
    }

    private static SalesOrder sameRequest(SalesOrder existing, String fingerprint) {
        if (!fingerprint.equals(existing.getRequestFingerprint())) {
            throw new ConflictException("DUPLICATE_REQUEST_KEY", "requestKey đã dùng cho một đơn khác");
        }
        return existing;
    }

    static String fingerprint(CreateOrderRequest req, String ref) {
        String items = req.items().stream()
                .collect(Collectors.groupingBy(QuoteItem::productId, Collectors.summingInt(QuoteItem::quantity)))
                .entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(","));
        return RequestFingerprint.of(String.join("|", String.valueOf(req.customerId()), req.paymentMethod().name(),
                String.valueOf(ref), items));
    }

    static Comparator<SalesOrder.Allocation> latestExpiryFirst() {
        return Comparator.comparing(SalesOrder.Allocation::getExpiryDate,
                Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder().reversed()));
    }
}
