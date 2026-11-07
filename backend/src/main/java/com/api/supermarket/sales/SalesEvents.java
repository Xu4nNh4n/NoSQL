package com.api.supermarket.sales;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bson.Document;

import com.api.supermarket.inventory.StockMovement;

/**
 * Payload sync_queue của đơn bán và phiếu trả: {@code movements} (thẻ kho), {@code payments} (sổ thanh toán),
 * {@code graph} (đơn → khách, sản phẩm, lô cho Neo4j).
 */
public final class SalesEvents {
    private SalesEvents() {
    }

    static LedgerPayment ledger(SalesOrder order, SalesOrder.Payment p) {
        return new LedgerPayment(StockMovement.idFor(p.getType().name(), p.getPaymentId()), order.getId(),
                order.getOrderCode(), p.getType().name(), p.getMethod().name(), p.getAmount(), p.getReference(),
                p.getReturnCode(), order.getCashier().id(), p.getAt());
    }

    public static Document orderCreated(SalesOrder order) {
        List<StockMovement> movements = new ArrayList<>();
        List<Document> items = new ArrayList<>();
        List<Document> allocations = new ArrayList<>();
        for (SalesOrder.OrderLine l : order.getItems()) {
            items.add(new Document("productId", l.getProductId()).append("sku", l.getSku()).append("name", l.getName())
                    .append("quantity", l.getQuantity()).append("gift", l.isGift()));
            for (SalesOrder.Allocation a : l.getAllocations()) {
                movements.add(new StockMovement(
                        StockMovement.idFor(StockMovement.SALE, order.getId(), String.valueOf(l.getLineNo()), a.getBatchId()),
                        l.getProductId(), order.getOrderedAt(), StockMovement.SALE, -a.getQuantity(), a.getBatchCode(),
                        "ORDER", order.getOrderCode(), l.isGift() ? "Hàng tặng" : null, order.getCashier().name()));
                allocations.add(new Document("batchId", a.getBatchId()).append("batchCode", a.getBatchCode())
                        .append("productId", l.getProductId()).append("quantity", a.getQuantity()));
            }
        }
        SalesOrder.PersonRef c = order.getCustomer();
        Document graph = new Document("orderId", order.getId()).append("orderCode", order.getOrderCode())
                .append("orderedAt", Date.from(order.getOrderedAt()))
                .append("customer", c == null ? null : new Document("id", c.id()).append("name", c.name()).append("phone", c.phone()))
                .append("items", items).append("allocations", allocations);
        return new Document("movements", movements.stream().map(StockMovement::toDocument).toList())
                .append("payments", order.getPayments().stream().map(p -> ledger(order, p).toDocument()).toList())
                .append("graph", graph);
    }

    public static Document returnCreated(SalesOrder order, List<StockMovement> movements, SalesOrder.Payment refund) {
        return new Document("movements", movements.stream().map(StockMovement::toDocument).toList())
                .append("payments", List.of(ledger(order, refund).toDocument()));
    }

    public static List<LedgerPayment> payments(Document payload) {
        List<Document> raw = payload == null ? null : payload.getList("payments", Document.class);
        return raw == null ? List.of() : raw.stream().map(LedgerPayment::fromDocument).toList();
    }
}
