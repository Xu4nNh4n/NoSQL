package com.api.supermarket.sales;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.promotion.Pricing.PricedCart;
import com.api.supermarket.sales.HeldCartService.HeldCart;
import com.api.supermarket.sales.HeldCartService.HoldRequest;
import com.api.supermarket.sales.OrderService.CreateOrderRequest;
import com.api.supermarket.sales.QuoteService.QuoteRequest;
import com.api.supermarket.sales.ReturnService.ReturnRequest;
import com.api.supermarket.sales.TopSalesService.TopSales;
import com.api.supermarket.security.JwtPrincipal;

import jakarta.validation.Valid;

/** Bán tại quầy, tra cứu đơn, trả hàng, đơn treo, top bán chạy. ADMIN và STAFF. */
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public class SalesController {
    private final QuoteService quotes;
    private final OrderService orders;
    private final ReturnService returns;
    private final PaymentLedger ledger;
    private final HeldCartService held;
    private final TopSalesService topSales;

    public SalesController(QuoteService quotes, OrderService orders, ReturnService returns, PaymentLedger ledger,
            HeldCartService held, TopSalesService topSales) {
        this.quotes = quotes;
        this.orders = orders;
        this.returns = returns;
        this.ledger = ledger;
        this.held = held;
        this.topSales = topSales;
    }

    @PostMapping("/api/orders/quote")
    public PricedCart quote(@Valid @RequestBody QuoteRequest req) {
        return quotes.quote(req.items());
    }

    @PostMapping("/api/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public SalesOrder create(@Valid @RequestBody CreateOrderRequest req, @AuthenticationPrincipal JwtPrincipal actor) {
        return orders.create(req, actor);
    }

    @GetMapping("/api/orders")
    public PageResponse<SalesOrder> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) SalesOrder.Status status,
            @RequestParam(required = false) SalesOrder.Method paymentMethod,
            @RequestParam(required = false) String cashierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "orderedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return orders.list(from, to, status, paymentMethod, cashierId, page, size, sortBy, sortDir);
    }

    @GetMapping("/api/orders/code/{code}")
    public SalesOrder byCode(@PathVariable String code) {
        return orders.byCode(code);
    }

    @GetMapping("/api/orders/{id}")
    public SalesOrder get(@PathVariable String id) {
        return orders.load(id);
    }

    /** Lịch sử thanh toán và hoàn tiền của đơn, đọc từ Cassandra (AP-14). */
    @GetMapping("/api/orders/{id}/payments")
    public List<LedgerPayment> payments(@PathVariable String id) {
        orders.load(id);
        return ledger.byOrder(id);
    }

    @PostMapping("/api/orders/{id}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    public SalesReturn createReturn(@PathVariable String id, @Valid @RequestBody ReturnRequest req,
            @AuthenticationPrincipal JwtPrincipal actor) {
        return returns.create(id, req, actor);
    }

    @GetMapping("/api/orders/{id}/returns")
    public List<SalesReturn> listReturns(@PathVariable String id) {
        return returns.listForOrder(id);
    }

    @GetMapping("/api/pos/held")
    public List<HeldCart> held(@AuthenticationPrincipal JwtPrincipal me) {
        return held.list(me.userId());
    }

    @PostMapping("/api/pos/held")
    @ResponseStatus(HttpStatus.CREATED)
    public HeldCart hold(@AuthenticationPrincipal JwtPrincipal me, @Valid @RequestBody HoldRequest req) {
        return held.hold(me.userId(), req);
    }

    /** Mở lại đơn treo: trả giỏ về quầy và xóa khỏi Redis. */
    @GetMapping("/api/pos/held/{holdId}")
    public HeldCart resume(@AuthenticationPrincipal JwtPrincipal me, @PathVariable String holdId) {
        return held.resume(me.userId(), holdId);
    }

    @DeleteMapping("/api/pos/held/{holdId}")
    public ResponseEntity<Void> deleteHeld(@AuthenticationPrincipal JwtPrincipal me, @PathVariable String holdId) {
        held.delete(me.userId(), holdId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/stats/top-sales/today")
    public TopSales topToday() {
        return topSales.today(10);
    }
}
