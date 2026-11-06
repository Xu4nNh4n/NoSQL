package com.api.supermarket.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import com.api.supermarket.domain.FefoPolicy;
import com.api.supermarket.domain.BatchAllocationData;
import com.api.supermarket.domain.ReturnItemData;
import com.api.supermarket.dto.request.ReceiveBatchRequest;
import com.api.supermarket.entity.*;
import com.api.supermarket.exception.*;
import com.api.supermarket.repository.*;
import com.api.supermarket.integration.DataSyncPublisher;

@Service
@Transactional
public class InventoryBatchService {
    private final InventoryBatchRepository batches;
    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final DataSyncPublisher dataSync;
    private final SupplierRepository suppliers;

    public InventoryBatchService(InventoryBatchRepository batches, ProductRepository products,
            StockMovementRepository movements, DataSyncPublisher dataSync, SupplierRepository suppliers) {
        this.batches = batches;
        this.products = products;
        this.movements = movements;
        this.dataSync = dataSync;
        this.suppliers = suppliers;
    }

    public InventoryBatch receive(ReceiveBatchRequest request) {
        Product product = products.findByIdForUpdate(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));
        String code = request.batchCode() == null ? "" : request.batchCode().trim();
        if (code.isBlank()) code = String.valueOf(batches.nextSimpleCodeValue());
        InventoryBatch existing = batches.findByCode(code).orElse(null);
        Supplier supplier = suppliers.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp"));
        String supplierName = supplier.supplierName;
        if (existing != null) {
            if (!existing.productId.equals(product.getProductId()) || existing.received != request.quantity()
                    || !Objects.equals(existing.supplierId, supplier.supplierId)
                    || existing.unitCost.compareTo(request.unitCost()) != 0
                    || !Objects.equals(existing.manufacturingDate, request.manufacturingDate())
                    || !Objects.equals(existing.expiryDate, request.expiryDate())) {
                throw new BadRequestException("Mã lô đã được nhập với nội dung khác");
            }
            return existing;
        }
        if (!supplier.isActive) throw new BadRequestException("Nhà cung cấp đã ngừng hợp tác; không thể nhập lô mới");
        validateDates(request.manufacturingDate(), request.expiryDate());
        if (!product.getIsActive()) throw new BadRequestException("Sản phẩm đã ngừng kinh doanh");
        refreshProductBalances(product);
        InventoryBatch batch = newBatch(product, code, request.quantity(), request.unitCost(), request.expiryDate(), supplierName);
        batch.manufacturingDate = request.manufacturingDate();
        batch.supplierId = supplier.supplierId;
        product.setStockQuantity(Math.addExact(product.getStockQuantity(), request.quantity()));
        products.save(product);
        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setMovementType("RECEIPT");
        movement.setQuantity(request.quantity());
        movement.setNote("Nhập lô " + code);
        movement.setPerformedBy(actor());
        dataSync.movement(movements.save(movement));
        return batches.save(batch);
    }

    @Transactional(readOnly = true)
    public int usableQuantity(Long productId) {
        return Math.toIntExact(batches.usableQuantity(productId, LocalDate.now()));
    }

    // Caller holds the product lock. Product balances are summaries of the locked lots.
    public int refreshProductBalances(Product product) {
        return refreshProductBalances(product, batches.lockProductBatches(product.getProductId()));
    }

    private int refreshProductBalances(Product product, List<InventoryBatch> locked) {
        if (locked.isEmpty() && (product.getStockQuantity() != 0 || product.getDamagedQuantity() != 0)) {
            throw new BadRequestException("Sản phẩm có tồn kho nhưng thiếu dữ liệu lô; cần đối soát trước khi xử lý");
        }
        int stock = 0, damaged = 0, usable = 0;
        LocalDate today = LocalDate.now();
        for (InventoryBatch batch : locked) {
            stock = Math.addExact(stock, batch.remaining);
            damaged = Math.addExact(damaged, batch.damaged);
            if ("ACTIVE".equals(batch.status) && (batch.expiryDate == null || !batch.expiryDate.isBefore(today))) {
                usable = Math.addExact(usable, batch.remaining);
            }
        }
        product.setStockQuantity(stock);
        product.setDamagedQuantity(damaged);
        return usable;
    }

    @Transactional(readOnly = true)
    public List<com.api.supermarket.dto.response.BatchAllocationResponse> getAllocations(SalesOrderItem item) {
        List<BatchAllocationData> lines = item.getBatchAllocations();
        Map<Long, InventoryBatch> byId = new HashMap<>();
        batches.findAllById(lines.stream().map(BatchAllocationData::batchId).toList())
                .forEach(batch -> byId.put(batch.id, batch));
        return lines.stream().map(line -> {
            InventoryBatch batch = byId.get(line.batchId());
            if (batch == null) throw new BadRequestException("Thiếu dữ liệu lô đã cấp phát");
            return new com.api.supermarket.dto.response.BatchAllocationResponse(batch.id, batch.code,
                    batch.expiryDate, line.quantity(), line.returnedQuantity(), line.status());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryBatch> list(Long productId) {
        if (productId == null) return batches.findAllByOrderByIdAsc();
        if (!products.existsById(productId)) throw new ResourceNotFoundException("Không tìm thấy sản phẩm");
        return batches.findByProductIdOrderByIdAsc(productId);
    }

    // Caller holds the product lock and changes aggregate stock in the same transaction.
    public InventoryBatch adjustBatch(Long batchId, com.api.supermarket.dto.request.StockAdjustmentRequest request) {
        if (request.getQuantity() >= 0) throw new BadRequestException("Điều chỉnh lô chỉ giảm tồn; tăng tồn dùng nhập lô mới");
        InventoryBatch original = batches.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lô"));
        Product product = products.findByIdForUpdate(original.productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));
        List<InventoryBatch> locked = batches.lockProductBatches(product.getProductId());
        refreshProductBalances(product, locked);
        InventoryBatch batch = locked.stream()
                .filter(candidate -> candidate.id.equals(batchId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lô"));
        long removed = -(long) request.getQuantity();
        if (removed > batch.remaining || removed > product.getStockQuantity()) {
            throw new BadRequestException("Không được giảm quá lượng chưa giữ của lô");
        }
        batch.remaining -= (int) removed;
        product.setStockQuantity(product.getStockQuantity() - (int) removed);
        products.save(product);
        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setMovementType("ADJUSTMENT");
        movement.setQuantity(request.getQuantity());
        String note = "Lô " + batch.code + ": " + request.getReason().trim();
        movement.setNote(note.substring(0, Math.min(255, note.length())));
        movement.setPerformedBy(actor());
        dataSync.movement(movements.save(movement));
        return batches.save(batch);
    }

    // Caller holds the product lock and changes aggregate stock in the same transaction.
    public void opening(Product product) {
        if (product.getStockQuantity() > 0) {
            batches.save(newBatch(product, String.valueOf(batches.nextSimpleCodeValue()), product.getStockQuantity(),
                    BigDecimal.ZERO, null, "Opening stock"));
        }
    }

    public void allocate(SalesOrderItem item) {
        List<InventoryBatch> locked = batches.lockProductBatches(item.getProduct().getProductId());
        Map<Long, InventoryBatch> byId = new HashMap<>();
        locked.forEach(batch -> byId.put(batch.id, batch));
        List<FefoPolicy.Lot> balances = locked.stream().filter(batch -> "ACTIVE".equals(batch.status))
                .map(batch -> new FefoPolicy.Lot(batch.id, batch.remaining,
                        batch.expiryDate, batch.receivedAt)).toList();
        List<FefoPolicy.Pick> plan;
        try {
            plan = FefoPolicy.allocate(balances, item.getQuantity(), LocalDate.now());
        } catch (IllegalArgumentException ex) {
            throw new InsufficientStockException(ex.getMessage());
        }
        List<BatchAllocationData> allocationData = new ArrayList<>();
        for (FefoPolicy.Pick pick : plan) {
            InventoryBatch batch = byId.get(pick.lotId());
            batch.remaining -= pick.quantity();
            allocationData.add(new BatchAllocationData(batch.id, pick.quantity(), 0, "CONSUMED"));
        }
        item.setBatchAllocations(allocationData);
        batches.saveAll(locked);
    }

    public List<ReturnItemData> receiveReturn(SalesOrderItem item, int quantity, boolean restock, Long batchId, String reason) {
        Map<Long, InventoryBatch> locked = lockMap(item);
        List<BatchAllocationData> lines = item.getBatchAllocations();
        if (lines.isEmpty()) {
            // Historical sales have no provable lot; quarantine rather than inventing provenance.
            if (restock || batchId != null) throw new BadRequestException("Đơn cũ thiếu nguồn gốc lô; nhận vào hàng cách ly với restock=false, không tự gán lô");
            InventoryBatch quarantine = newBatch(item.getProduct(), "LEGACY-RETURN-" + UUID.randomUUID(),
                    0, BigDecimal.ZERO, null, "Unknown");
            quarantine.damaged = quantity;
            quarantine.status = "QUARANTINED";
            batches.save(quarantine);
            return List.of(new ReturnItemData(item.getOrderItemId(), null, quantity, false, reason));
        }
        List<BatchAllocationData> outstanding = lines.stream().filter(line -> line.returnedQuantity() < line.quantity()).toList();
        long outstandingQuantity = outstanding.stream().mapToLong(line -> line.quantity() - line.returnedQuantity()).sum();
        if (batchId == null && outstanding.size() > 1 && quantity != outstandingQuantity) {
            throw new BadRequestException("Trả một phần món xuất từ nhiều lô phải xác định batchId trên hàng thực tế");
        }
        if (batchId != null && outstanding.stream().noneMatch(line -> line.batchId().equals(batchId))) {
            throw new BadRequestException("Lô trả không thuộc phần hàng đã xuất còn được trả của món này");
        }
        int remaining = quantity;
        List<BatchAllocationData> updated = new ArrayList<>(lines.size());
        List<ReturnItemData> received = new ArrayList<>();
        for (BatchAllocationData line : lines) {
            if (remaining == 0 || (batchId != null && !line.batchId().equals(batchId))) { updated.add(line); continue; }
            int receive = Math.min(remaining, line.quantity() - line.returnedQuantity());
            if (receive <= 0) { updated.add(line); continue; }
            InventoryBatch batch = locked.get(line.batchId());
            if (!"CONSUMED".equals(line.status()) || batch == null) {
                throw new BadRequestException("Mặt hàng chưa được xuất từ lô");
            }
            if (restock) {
                if (!"ACTIVE".equals(batch.status)
                        || (batch.expiryDate != null && batch.expiryDate.isBefore(LocalDate.now()))) {
                    throw new BadRequestException("Lô trả đã hết hạn/bị cách ly; phải nhận với restock=false");
                }
                batch.remaining = Math.addExact(batch.remaining, receive);
            } else batch.damaged = Math.addExact(batch.damaged, receive);
            updated.add(new BatchAllocationData(line.batchId(), line.quantity(),
                    line.returnedQuantity() + receive, line.status()));
            received.add(new ReturnItemData(item.getOrderItemId(), line.batchId(), receive, restock, reason));
            remaining -= receive;
        }
        if (remaining != 0) throw new BadRequestException("Số lượng trả vượt lượng đã xuất từ lô");
        batches.saveAll(locked.values());
        item.setBatchAllocations(updated);
        return received;
    }

    private Map<Long, InventoryBatch> lockMap(SalesOrderItem item) {
        Map<Long, InventoryBatch> result = new HashMap<>();
        batches.lockProductBatches(item.getProduct().getProductId()).forEach(batch -> result.put(batch.id, batch));
        return result;
    }

    private InventoryBatch newBatch(Product product, String code, int quantity, BigDecimal cost, LocalDate expiry, String supplierName) {
        InventoryBatch batch = new InventoryBatch();
        batch.productId = product.getProductId();
        batch.supplierName = supplierName;
        batch.code = code;
        batch.received = quantity;
        batch.remaining = quantity;
        batch.unitCost = cost;
        batch.expiryDate = expiry;
        batch.receivedAt = LocalDateTime.now();
        batch.receivedBy = actor();
        return batch;
    }

    private void validateDates(LocalDate manufactured, LocalDate expiry) {
        if (manufactured != null && manufactured.isAfter(LocalDate.now()))
            throw new BadRequestException("Ngày sản xuất không được ở tương lai");
        if (expiry != null && (expiry.isBefore(LocalDate.now())
                || (manufactured != null && expiry.isBefore(manufactured))))
            throw new BadRequestException("Hạn sử dụng không hợp lệ");
    }

    private String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "SYSTEM" : authentication.getName();
    }
}
