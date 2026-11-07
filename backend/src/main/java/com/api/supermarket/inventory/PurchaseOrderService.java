package com.api.supermarket.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.api.supermarket.catalog.product.Product;
import com.api.supermarket.catalog.product.ProductCache;
import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.catalog.supplier.Supplier;
import com.api.supermarket.catalog.supplier.SupplierService;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.CodeGenerator;
import com.api.supermarket.common.ConflictException;
import com.api.supermarket.common.DocumentType;
import com.api.supermarket.common.MongoTx;
import com.api.supermarket.common.RetryableConflictException;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.inventory.BatchService.ReceiveCommand;
import com.api.supermarket.inventory.BatchService.Received;
import com.api.supermarket.security.JwtPrincipal;
import com.api.supermarket.sync.SyncQueue;
import com.api.supermarket.user.UserFields;
import com.api.supermarket.validation.PaginationValidator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Phiếu nhập (FR-05): tạo nháp, sửa hoặc xóa khi còn nháp, nhận hàng. Nhận hàng là một transaction:
 * tạo các lô, cộng tồn, đặt phiếu RECEIVED và xếp một sự kiện BATCH_RECEIVED cho cả phiếu.
 */
@Service
public class PurchaseOrderService {
    public static final Set<String> SORT_FIELDS = Set.of("createdAt", "poCode");
    static final int MAX_LINES = 100;

    public record LineRequest(@NotBlank String productId, @Min(1) int orderedQty,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 0) BigDecimal unitCost) {
    }

    public record PurchaseOrderRequest(@NotBlank String supplierId, @Size(max = 500) String note,
            @NotEmpty @Size(max = MAX_LINES) List<@Valid LineRequest> lines, Long version) {
    }

    public record ReceiveLine(@Min(1) int lineNo, @Min(0) int receivedQty, LocalDate mfgDate, LocalDate expiryDate) {
    }

    public record ReceiveRequest(@NotEmpty List<@Valid ReceiveLine> lines) {
    }

    private final PurchaseOrderRepository orders;
    private final ProductRepository products;
    private final SupplierService suppliers;
    private final BatchService batches;
    private final CodeGenerator codes;
    private final SyncQueue sync;
    private final MongoTx tx;
    private final ProductCache cache;
    private final AppTime time;

    public PurchaseOrderService(PurchaseOrderRepository orders, ProductRepository products, SupplierService suppliers,
            BatchService batches, CodeGenerator codes, SyncQueue sync, MongoTx tx, ProductCache cache, AppTime time) {
        this.orders = orders;
        this.products = products;
        this.suppliers = suppliers;
        this.batches = batches;
        this.codes = codes;
        this.sync = sync;
        this.tx = tx;
        this.cache = cache;
        this.time = time;
    }

    public PageResponse<PurchaseOrder> list(PurchaseOrder.Status status, String supplierId, int page, int size,
            String sortBy, String sortDir) {
        return PageResponse.of(orders.search(status, supplierId,
                PaginationValidator.pageRequest(page, size, sortBy, sortDir, SORT_FIELDS)));
    }

    public PurchaseOrder load(String id) {
        return orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu nhập"));
    }

    public PurchaseOrder create(PurchaseOrderRequest req, JwtPrincipal actor) {
        PurchaseOrder po = new PurchaseOrder();
        applyDraft(po, req);
        po.setPoCode(codes.next(DocumentType.PURCHASE_ORDER));
        po.setStatus(PurchaseOrder.Status.DRAFT);
        po.setCreatedBy(actor.username());
        po.setCreatedAt(time.now());
        po.setUpdatedAt(po.getCreatedAt());
        return orders.save(po);
    }

    public PurchaseOrder update(String id, PurchaseOrderRequest req) {
        PurchaseOrder po = requireDraft(load(id));
        if (req.version() == null || !Objects.equals(req.version(), po.getVersion())) {
            throw new ConflictException("STALE_VERSION", "Phiếu nhập vừa được người khác cập nhật, vui lòng tải lại");
        }
        applyDraft(po, req);
        po.setUpdatedAt(time.now());
        return orders.save(po);
    }

    public void delete(String id) {
        orders.delete(requireDraft(load(id)));
    }

    /**
     * Nhận hàng. Phiếu đã RECEIVED thì trả nguyên kết quả cũ, không cộng tồn lần hai (NFR-04).
     * Hai người bấm nhận cùng lúc: @Version làm người sau xung đột, chạy lại và thấy phiếu đã nhận.
     */
    public PurchaseOrder receive(String id, ReceiveRequest req, JwtPrincipal actor) {
        boolean[] changed = {false};
        PurchaseOrder result = tx.run(() -> {
            PurchaseOrder po = load(id);
            if (po.getStatus() == PurchaseOrder.Status.RECEIVED) {
                return po;
            }
            Map<Integer, ReceiveLine> input = req.lines().stream()
                    .collect(Collectors.toMap(ReceiveLine::lineNo, Function.identity(), (a, b) -> {
                        throw new BadRequestException("Dòng " + a.lineNo() + " bị nhập hai lần");
                    }));
            Set<Integer> known = po.getLines().stream().map(PurchaseOrder.Line::getLineNo).collect(Collectors.toSet());
            for (Integer lineNo : input.keySet()) {
                if (!known.contains(lineNo)) {
                    throw new BadRequestException("Phiếu không có dòng " + lineNo);
                }
            }
            if (input.values().stream().noneMatch(l -> l.receivedQty() > 0)) {
                throw new BadRequestException("Phải nhận ít nhất một dòng có số lượng lớn hơn 0");
            }
            suppliers.loadActive(po.getSupplierId());

            List<StockMovement> movements = new ArrayList<>();
            List<Batch> created = new ArrayList<>();
            for (PurchaseOrder.Line line : po.getLines()) {
                ReceiveLine in = input.get(line.getLineNo());
                if (in == null || in.receivedQty() == 0) {
                    continue;
                }
                Received r = batches.receiveInTx(new ReceiveCommand(line.getProductId(), po.getSupplierId(),
                        in.receivedQty(), line.getUnitCost(), in.mfgDate(), in.expiryDate(), po.getId(),
                        po.getPoCode()), actor);
                line.setReceivedQty(in.receivedQty());
                line.setMfgDate(in.mfgDate());
                line.setExpiryDate(in.expiryDate());
                line.setBatchId(r.batch().getId());
                line.setBatchCode(r.batch().getBatchCode());
                movements.add(r.movement());
                created.add(r.batch());
            }
            po.setStatus(PurchaseOrder.Status.RECEIVED);
            po.setReceivedBy(actor.username());
            po.setReceivedAt(time.now());
            po.setUpdatedAt(po.getReceivedAt());
            PurchaseOrder saved;
            try {
                saved = orders.save(po);
            } catch (OptimisticLockingFailureException ex) {
                throw new RetryableConflictException("Phiếu nhập vừa được nhận bởi người khác");
            }
            sync.enqueue(InventoryEvents.BATCH_RECEIVED, po.getId(), InventoryEvents.payload(movements, created),
                    actor.userId());
            changed[0] = true;
            return saved;
        });
        if (changed[0]) {
            cache.evict();
        }
        return result;
    }

    private void applyDraft(PurchaseOrder po, PurchaseOrderRequest req) {
        Supplier supplier = suppliers.loadActive(req.supplierId());
        Set<String> seen = new HashSet<>();
        for (LineRequest l : req.lines()) {
            if (!seen.add(l.productId())) {
                throw new BadRequestException("Một sản phẩm chỉ xuất hiện một dòng trong phiếu");
            }
        }
        List<PurchaseOrder.Line> lines = new ArrayList<>();
        int no = 1;
        for (LineRequest l : req.lines()) {
            Product p = products.findById(l.productId())
                    .orElseThrow(() -> new BadRequestException("Sản phẩm không tồn tại: " + l.productId()));
            if (!p.isActive()) {
                throw new BadRequestException("Sản phẩm " + p.getSku() + " đã ngừng kinh doanh");
            }
            PurchaseOrder.Line line = new PurchaseOrder.Line();
            line.setLineNo(no++);
            line.setProductId(p.getId());
            line.setSku(p.getSku());
            line.setProductName(p.getName());
            line.setOrderedQty(l.orderedQty());
            line.setUnitCost(l.unitCost());
            lines.add(line);
        }
        po.setSupplierId(supplier.getId());
        po.setSupplierName(supplier.getName());
        po.setNote(UserFields.text(req.note()));
        po.setLines(lines);
    }

    private static PurchaseOrder requireDraft(PurchaseOrder po) {
        if (po.getStatus() != PurchaseOrder.Status.DRAFT) {
            throw new BadRequestException("Phiếu đã nhận hàng, không sửa hoặc xóa được");
        }
        return po;
    }
}
