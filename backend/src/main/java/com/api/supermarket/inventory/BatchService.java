package com.api.supermarket.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.api.supermarket.catalog.product.Product;
import com.api.supermarket.catalog.product.ProductCache;
import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.catalog.supplier.Supplier;
import com.api.supermarket.catalog.supplier.SupplierService;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.CodeGenerator;
import com.api.supermarket.common.DocumentType;
import com.api.supermarket.common.MongoTx;
import com.api.supermarket.common.RetryableConflictException;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.security.JwtPrincipal;
import com.api.supermarket.sync.SyncQueue;
import com.api.supermarket.validation.PaginationValidator;

/** Lô hàng: nhập lô (lẻ hoặc từ phiếu nhập), điều chỉnh giảm (FR-05), tra cứu lô. */
@Service
public class BatchService {
    public static final Set<String> SORT_FIELDS = Set.of("expirySortKey", "receivedAt", "batchCode");

    /** Một dòng nhận hàng. {@code purchaseOrderId}/{@code purchaseOrderCode} null khi nhập lô lẻ. */
    public record ReceiveCommand(String productId, String supplierId, int quantity, BigDecimal unitCost,
            LocalDate mfgDate, LocalDate expiryDate, String purchaseOrderId, String purchaseOrderCode) {
    }

    /** Kết quả nhận một lô: lô vừa tạo và dòng thẻ kho RECEIPT tương ứng. */
    public record Received(Batch batch, StockMovement movement) {
    }

    private final BatchRepository batches;
    private final ProductRepository products;
    private final SupplierService suppliers;
    private final CodeGenerator codes;
    private final SyncQueue sync;
    private final MongoTx tx;
    private final ProductCache cache;
    private final AppTime time;

    public BatchService(BatchRepository batches, ProductRepository products, SupplierService suppliers,
            CodeGenerator codes, SyncQueue sync, MongoTx tx, ProductCache cache, AppTime time) {
        this.batches = batches;
        this.products = products;
        this.suppliers = suppliers;
        this.codes = codes;
        this.sync = sync;
        this.tx = tx;
        this.cache = cache;
        this.time = time;
    }

    public Batch load(String id) {
        return batches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lô hàng"));
    }

    public PageResponse<Batch> list(String productId, int page, int size, String sortBy, String sortDir) {
        return PageResponse.of(batches.search(productId,
                PaginationValidator.pageRequest(page, size, sortBy, sortDir, SORT_FIELDS)));
    }

    public List<Batch> expired() {
        return batches.findExpired(time.today());
    }

    /** Nhập lô lẻ (hàng phát sinh ngoài phiếu nhập): một transaction gồm lô, tồn sản phẩm và sự kiện đồng bộ. */
    public Batch receiveLoose(ReceiveCommand cmd, JwtPrincipal actor) {
        Batch batch = tx.run(() -> {
            Received r = receiveInTx(cmd, actor);
            sync.enqueue(InventoryEvents.BATCH_RECEIVED, r.batch().getId(),
                    InventoryEvents.payload(List.of(r.movement()), List.of(r.batch())), actor.userId());
            return r.batch();
        });
        cache.evict();
        return batch;
    }

    /** Tạo một lô và cộng tồn; người gọi phải đang ở trong transaction và tự xếp sự kiện đồng bộ. */
    public Received receiveInTx(ReceiveCommand cmd, JwtPrincipal actor) {
        LocalDate today = time.today();
        if (cmd.quantity() <= 0) {
            throw new BadRequestException("Số lượng nhập phải lớn hơn 0");
        }
        if (cmd.unitCost() == null || cmd.unitCost().signum() < 0) {
            throw new BadRequestException("Giá vốn không hợp lệ");
        }
        if (cmd.mfgDate() != null && cmd.mfgDate().isAfter(today)) {
            throw new BadRequestException("Ngày sản xuất không được ở tương lai");
        }
        if (cmd.expiryDate() != null && cmd.expiryDate().isBefore(today)) {
            throw new BadRequestException("Hạn dùng không được trước hôm nay");
        }
        if (cmd.expiryDate() != null && cmd.mfgDate() != null && cmd.expiryDate().isBefore(cmd.mfgDate())) {
            throw new BadRequestException("Hạn dùng không được trước ngày sản xuất");
        }
        Product product = products.findById(cmd.productId())
                .orElseThrow(() -> new BadRequestException("Sản phẩm không tồn tại"));
        if (!product.isActive()) {
            throw new BadRequestException("Sản phẩm " + product.getSku() + " đã ngừng kinh doanh");
        }
        String supplierId = cmd.supplierId() != null ? cmd.supplierId() : product.getSupplierId();
        Supplier supplier = supplierId == null ? null : suppliers.loadActive(supplierId);

        Batch b = new Batch();
        b.setBatchCode(codes.next(DocumentType.BATCH));
        b.setProductId(product.getId());
        b.setProductSku(product.getSku());
        b.setProductName(product.getName());
        b.setSupplierId(supplier == null ? null : supplier.getId());
        b.setSupplierName(supplier == null ? null : supplier.getName());
        b.setPurchaseOrderId(cmd.purchaseOrderId());
        b.setPurchaseOrderCode(cmd.purchaseOrderCode());
        b.setReceivedQty(cmd.quantity());
        b.setRemainingQty(cmd.quantity());
        b.setUnitCost(cmd.unitCost());
        b.setMfgDate(cmd.mfgDate());
        b.setExpiryDate(cmd.expiryDate());
        b.setExpirySortKey(cmd.expiryDate() == null ? Batch.NO_EXPIRY : cmd.expiryDate());
        b.setReceivedAt(time.now());
        b.setReceivedBy(actor.username());
        Batch saved = batches.insert(b);
        products.incStock(product.getId(), cmd.quantity(), 0);

        String refType = cmd.purchaseOrderId() == null ? "BATCH" : "PURCHASE_ORDER";
        String refCode = cmd.purchaseOrderCode() == null ? saved.getBatchCode() : cmd.purchaseOrderCode();
        StockMovement movement = new StockMovement(StockMovement.idFor(StockMovement.RECEIPT, saved.getId()),
                product.getId(), saved.getReceivedAt(), StockMovement.RECEIPT, cmd.quantity(), saved.getBatchCode(),
                refType, refCode, null, actor.username());
        return new Received(saved, movement);
    }

    /** Điều chỉnh giảm tồn theo lô (hao hụt, hư hỏng, hết hạn, thu hồi). Muốn tăng phải nhập lô. */
    public Batch adjust(String batchId, int quantity, String reason, JwtPrincipal actor) {
        if (quantity <= 0) {
            throw new BadRequestException("Số lượng điều chỉnh phải lớn hơn 0");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Phải nhập lý do điều chỉnh");
        }
        String why = reason.trim();
        Batch result = tx.run(() -> {
            Batch b = load(batchId);
            if (quantity > b.getRemainingQty()) {
                throw new BadRequestException("Số lượng điều chỉnh vượt tồn còn lại của lô (" + b.getRemainingQty() + ")");
            }
            if (!batches.decrementIfEnough(batchId, quantity)) {
                throw new RetryableConflictException("Tồn lô vừa thay đổi");
            }
            products.incStock(b.getProductId(), -quantity, 0);
            String adjustmentId = UUID.randomUUID().toString();
            var at = time.now();
            batches.pushAdjustment(batchId, new Batch.Adjustment(adjustmentId, quantity, why, actor.username(), at));
            StockMovement movement = new StockMovement(StockMovement.idFor(StockMovement.ADJUSTMENT, adjustmentId),
                    b.getProductId(), at, StockMovement.ADJUSTMENT, -quantity, b.getBatchCode(), "ADJUSTMENT",
                    b.getBatchCode(), "Lô " + b.getBatchCode() + ": " + why, actor.username());
            sync.enqueue(InventoryEvents.STOCK_ADJUSTED, batchId, InventoryEvents.payload(List.of(movement), List.of()),
                    actor.userId());
            return load(batchId);
        });
        cache.evict();
        return result;
    }
}
