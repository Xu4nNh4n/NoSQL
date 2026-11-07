package com.api.supermarket.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.api.supermarket.catalog.product.ProductCache;
import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.CodeGenerator;
import com.api.supermarket.common.ConflictException;
import com.api.supermarket.common.DocumentType;
import com.api.supermarket.common.MongoTx;
import com.api.supermarket.common.RetryableConflictException;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.inventory.BatchRepository;
import com.api.supermarket.inventory.InventoryEvents;
import com.api.supermarket.inventory.StockMovement;
import com.api.supermarket.security.JwtPrincipal;
import com.api.supermarket.sync.SyncQueue;
import com.api.supermarket.user.UserFields;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Trả hàng kèm hoàn tiền một bước (FR-08). Một transaction: kiểm tra hạn trả và số lượng, trả về đúng lô đã xuất
 * (lô hạn xa nhất trước; lô đã hết hạn bắt buộc tính hàng hỏng), tính tiền hoàn theo {@link ReturnPolicy},
 * ghi phiếu trả, thêm khoản REFUND vào đơn và xếp sự kiện RETURN_CREATED.
 */
@Service
public class ReturnService {
    public record ReturnItemRequest(@Min(1) int lineNo, @Min(1) int quantity, Boolean restock,
            @NotBlank @Size(max = 200) String reason) {
    }

    public record ReturnRequest(@NotBlank @Size(max = 64) String requestKey,
            @NotEmpty @Size(max = 100) List<@Valid ReturnItemRequest> items,
            @NotNull SalesOrder.Method refundMethod, @Size(max = 100) String refundReference) {
    }

    private final OrderRepository orders;
    private final BatchRepository batches;
    private final ProductRepository products;
    private final CodeGenerator codes;
    private final SyncQueue sync;
    private final MongoTx tx;
    private final ProductCache cache;
    private final AppTime time;
    private final int windowDays;

    public ReturnService(OrderRepository orders, BatchRepository batches, ProductRepository products, CodeGenerator codes,
            SyncQueue sync, MongoTx tx, ProductCache cache, AppTime time,
            @Value("${app.return-window-days:7}") int windowDays) {
        this.orders = orders;
        this.batches = batches;
        this.products = products;
        this.codes = codes;
        this.sync = sync;
        this.tx = tx;
        this.cache = cache;
        this.time = time;
        this.windowDays = windowDays;
    }

    public List<SalesReturn> listForOrder(String orderId) {
        return orders.findReturnsByOrder(orderId);
    }

    public SalesReturn create(String orderId, ReturnRequest req, JwtPrincipal actor) {
        String ref = UserFields.text(req.refundReference());
        if (req.refundMethod() == SalesOrder.Method.BANK_TRANSFER && ref == null) {
            throw new BadRequestException("Hoàn chuyển khoản phải nhập mã tham chiếu");
        }
        String fingerprint = fingerprint(orderId, req, ref);
        SalesReturn existing = orders.findReturnByRequestKey(req.requestKey()).orElse(null);
        if (existing != null) {
            return same(existing, fingerprint);
        }
        SalesReturn result;
        try {
            result = tx.run(() -> createInTx(orderId, req, ref, fingerprint, actor));
        } catch (DuplicateKeyException ex) {
            existing = orders.findReturnByRequestKey(req.requestKey()).orElseThrow(() -> ex);
            return same(existing, fingerprint);
        }
        cache.evict();
        return result;
    }

    private SalesReturn createInTx(String orderId, ReturnRequest req, String ref, String fingerprint, JwtPrincipal actor) {
        SalesOrder order = orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));
        if (order.getStatus() == SalesOrder.Status.RETURNED) {
            throw new BadRequestException("Đơn đã trả hết hàng");
        }
        LocalDate today = time.today();
        if (order.getOrderDay().plusDays(windowDays).isBefore(today)) {
            throw new BadRequestException("Đã quá hạn trả hàng " + windowDays + " ngày");
        }
        Map<Integer, SalesOrder.OrderLine> lines = order.getItems().stream()
                .collect(Collectors.toMap(SalesOrder.OrderLine::getLineNo, Function.identity()));
        Set<Integer> seen = new HashSet<>();

        String returnId = new ObjectId().toHexString();
        var now = time.now();
        SalesReturn ret = new SalesReturn();
        ret.setId(returnId);
        List<StockMovement> movements = new ArrayList<>();
        BigDecimal refundTotal = BigDecimal.ZERO;

        for (ReturnItemRequest in : req.items()) {
            if (!seen.add(in.lineNo())) {
                throw new BadRequestException("Dòng " + in.lineNo() + " bị nhập hai lần");
            }
            SalesOrder.OrderLine line = lines.get(in.lineNo());
            if (line == null) {
                throw new BadRequestException("Đơn không có dòng " + in.lineNo());
            }
            int left = line.getQuantity() - line.getReturnedQuantity();
            if (in.quantity() > left) {
                throw new BadRequestException("Dòng " + in.lineNo() + " chỉ còn " + left + " có thể trả");
            }
            boolean restock = in.restock() == null || in.restock();
            BigDecimal refund = ReturnPolicy.refund(line.getNetAmount(), line.getQuantity(), line.getReturnedQuantity(),
                    in.quantity());

            SalesReturn.Item item = new SalesReturn.Item();
            item.setLineNo(line.getLineNo());
            item.setProductId(line.getProductId());
            item.setSku(line.getSku());
            item.setName(line.getName());
            item.setQuantity(in.quantity());
            item.setRestock(restock);
            item.setReason(in.reason().trim());
            item.setRefundAmount(refund);

            int remaining = in.quantity();
            List<SalesOrder.Allocation> allocs = new ArrayList<>(line.getAllocations());
            allocs.sort(OrderService.latestExpiryFirst());
            for (SalesOrder.Allocation a : allocs) {
                int take = Math.min(remaining, a.getQuantity() - a.getReturnedQuantity());
                if (take <= 0) {
                    continue;
                }
                if (restock && a.getExpiryDate() != null && a.getExpiryDate().isBefore(today)) {
                    throw new BadRequestException("Lô " + a.getBatchCode() + " đã hết hạn, phải trả dạng hàng hỏng");
                }
                batches.returnToBatch(a.getBatchId(), take, restock);
                products.incStock(line.getProductId(), restock ? take : 0, restock ? 0 : take);
                a.setReturnedQuantity(a.getReturnedQuantity() + take);
                item.getBatches().add(new SalesReturn.BatchPart(a.getBatchId(), a.getBatchCode(), take));
                String type = restock ? StockMovement.RETURN : StockMovement.RETURN_DAMAGED;
                movements.add(new StockMovement(
                        StockMovement.idFor(type, returnId, String.valueOf(line.getLineNo()), a.getBatchId()),
                        line.getProductId(), now, type, take, a.getBatchCode(), "RETURN", null, item.getReason(),
                        actor.username()));
                remaining -= take;
                if (remaining == 0) {
                    break;
                }
            }
            if (remaining != 0) {
                throw new IllegalStateException("Dữ liệu cấp phát lô của dòng " + line.getLineNo() + " không khớp");
            }
            line.setReturnedQuantity(line.getReturnedQuantity() + in.quantity());
            refundTotal = refundTotal.add(refund);
            ret.getItems().add(item);
        }

        BigDecimal paid = order.getTotal().subtract(order.getRefundedTotal());
        if (refundTotal.compareTo(paid) > 0) {
            throw new BadRequestException("Tiền hoàn vượt số tiền còn lại của đơn");
        }
        String returnCode = codes.next(DocumentType.RETURN);
        ret.setReturnCode(returnCode);
        ret.setOrderId(order.getId());
        ret.setOrderCode(order.getOrderCode());
        ret.setRequestKey(req.requestKey());
        ret.setRequestFingerprint(fingerprint);
        ret.setRefundAmount(refundTotal);
        ret.setRefundMethod(req.refundMethod());
        ret.setRefundReference(ref);
        ret.setCreatedBy(new SalesOrder.PersonRef(actor.userId(), actor.username(), null));
        ret.setCreatedAt(now);
        List<StockMovement> withCode = movements.stream().map(m -> new StockMovement(m.id(), m.productId(), m.createdAt(),
                m.type(), m.quantity(), m.batchCode(), m.refType(), returnCode, m.note(), m.performedBy())).toList();

        SalesOrder.Payment refund = new SalesOrder.Payment();
        refund.setPaymentId("REF-" + returnId);
        refund.setType(SalesOrder.Payment.Type.REFUND);
        refund.setMethod(req.refundMethod());
        refund.setAmount(refundTotal);
        refund.setReference(ref != null ? ref : "REFUND-" + returnCode);
        refund.setReturnId(returnId);
        refund.setReturnCode(returnCode);
        refund.setAt(now);
        refund.setBy(actor.username());
        order.getPayments().add(refund);
        order.setRefundedTotal(order.getRefundedTotal().add(refundTotal));
        boolean allReturned = order.getItems().stream().allMatch(l -> l.getReturnedQuantity() == l.getQuantity());
        order.setStatus(allReturned ? SalesOrder.Status.RETURNED : SalesOrder.Status.PARTIALLY_RETURNED);
        try {
            orders.save(order);
        } catch (OptimisticLockingFailureException ex) {
            throw new RetryableConflictException("Đơn vừa có phiếu trả khác");
        }
        SalesReturn saved = orders.insertReturn(ret);
        sync.enqueue(InventoryEvents.RETURN_CREATED, returnId, SalesEvents.returnCreated(order, withCode, refund),
                actor.userId());
        return saved;
    }

    private static SalesReturn same(SalesReturn existing, String fingerprint) {
        if (!fingerprint.equals(existing.getRequestFingerprint())) {
            throw new ConflictException("DUPLICATE_REQUEST_KEY", "requestKey đã dùng cho một phiếu trả khác");
        }
        return existing;
    }

    static String fingerprint(String orderId, ReturnRequest req, String ref) {
        String items = req.items().stream().sorted(java.util.Comparator.comparingInt(ReturnItemRequest::lineNo))
                .map(i -> i.lineNo() + ":" + i.quantity() + ":" + (i.restock() == null || i.restock()))
                .collect(Collectors.joining(","));
        return RequestFingerprint.of(String.join("|", orderId, req.refundMethod().name(), String.valueOf(ref), items));
    }
}
