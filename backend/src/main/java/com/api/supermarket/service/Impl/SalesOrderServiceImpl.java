package com.api.supermarket.service.Impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.supermarket.dto.request.OrderItemRequest;
import com.api.supermarket.dto.request.PaymentConfirmationRequest;
import com.api.supermarket.dto.request.ReturnItemRequest;
import com.api.supermarket.dto.request.ReturnOrderRequest;
import com.api.supermarket.dto.request.SalesOrderRequest;
import com.api.supermarket.dto.response.OrderItemResponse;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.dto.response.ProductResponse;
import com.api.supermarket.dto.response.SalesOrderResponse;
import com.api.supermarket.entity.Product;
import com.api.supermarket.entity.PaymentRecord;
import com.api.supermarket.entity.SalesOrder;
import com.api.supermarket.entity.SalesOrderItem;
import com.api.supermarket.entity.SalesOrderReturn;
import com.api.supermarket.domain.ReturnItemData;
import com.api.supermarket.entity.StockMovement;
import com.api.supermarket.entity.User;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.InsufficientStockException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.repository.ProductRepository;
import com.api.supermarket.repository.PaymentRecordRepository;
import com.api.supermarket.repository.SalesOrderItemRepository;
import com.api.supermarket.repository.SalesOrderReturnRepository;
import com.api.supermarket.repository.SalesOrderRepository;
import com.api.supermarket.repository.StockMovementRepository;
import com.api.supermarket.repository.UserRepository;
import com.api.supermarket.service.SalesOrderService;
import com.api.supermarket.validation.PaginationValidator;
import com.api.supermarket.integration.DataSyncPublisher;

@Service
public class SalesOrderServiceImpl implements SalesOrderService {
    private static final String IN_STORE = "IN_STORE";
    private static final String CASH = "CASH";
    private static final String BANK_TRANSFER = "BANK_TRANSFER";

    private final SalesOrderItemRepository salesOrderItemRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StockMovementRepository stockMovementRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final SalesOrderReturnRepository salesOrderReturnRepository;
    private final com.api.supermarket.service.InventoryBatchService inventoryBatchService;
    private final DataSyncPublisher dataSync;
    private final com.api.supermarket.service.PromotionService promotions;

    public SalesOrderServiceImpl(SalesOrderRepository salesOrderRepository,
            ProductRepository productRepository, SalesOrderItemRepository salesOrderItemRepository,
            UserRepository userRepository, StockMovementRepository stockMovementRepository,
            PaymentRecordRepository paymentRecordRepository, SalesOrderReturnRepository salesOrderReturnRepository,
            com.api.supermarket.service.InventoryBatchService inventoryBatchService, DataSyncPublisher dataSync,
            com.api.supermarket.service.PromotionService promotions) {
        this.salesOrderRepository = salesOrderRepository;
        this.productRepository = productRepository;
        this.salesOrderItemRepository = salesOrderItemRepository;
        this.userRepository = userRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.salesOrderReturnRepository = salesOrderReturnRepository;
        this.inventoryBatchService = inventoryBatchService;
        this.dataSync = dataSync;
        this.promotions = promotions;
    }

    @Override
    // Do not reuse the staff lookup's snapshot when promotions change during a product-lock wait.
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SalesOrderResponse createSalesOrder(SalesOrderRequest request) {
        return createOrder(request);
    }

    @Override
    @Transactional(readOnly = true)
    public com.api.supermarket.dto.response.PosQuoteResponse quote(com.api.supermarket.dto.request.PosQuoteRequest request) {
        Map<Long, Integer> quantities = mergeQuantities(request.items());
        Map<Long, Product> catalog = new TreeMap<>();
        quantities.keySet().forEach(id -> catalog.put(id, productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + id))));
        return promotions.quote(quantities, catalog,
                LocalDateTime.now(com.api.supermarket.service.PromotionService.VIETNAM));
    }

    private SalesOrderResponse createOrder(SalesOrderRequest request) {
        if (request == null) {
            throw new BadRequestException("Dữ liệu đơn hàng không được để trống");
        }
        String orderType = normalize(request.getOrderType());
        String paymentMethod = normalize(request.getPaymentMethod());
        if (!IN_STORE.equals(orderType)) {
            throw new BadRequestException("Hệ thống hiện chỉ nhận đơn bán tại quầy POS");
        }
        validateOrderInput(paymentMethod, request);

        User user = currentUser();
        if (user == null || !isStaff()) {
            throw new BadRequestException("Đơn tại cửa hàng phải có nhân viên thao tác");
        }

        Map<Long, Integer> quantities = mergeQuantities(request.getItems());
        if (quantities.isEmpty()) {
            throw new BadRequestException("Đơn hàng phải có ít nhất một sản phẩm");
        }

        // Lock products in ID order, then reprice before any inventory/payment writes.
        Map<Long, Product> catalog = new TreeMap<>();
        quantities.keySet().forEach(id -> catalog.put(id, productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + id))));
        LocalDateTime pricedAt = LocalDateTime.now(com.api.supermarket.service.PromotionService.VIETNAM);
        var quote = promotions.quote(quantities, catalog, pricedAt);
        if (!quote.quoteToken().equals(request.getQuoteToken())) {
            throw new com.api.supermarket.exception.PriceChangedException(quote);
        }
        Map<Long, com.api.supermarket.dto.response.PosQuoteResponse.Item> prices = quote.items().stream()
                .collect(java.util.stream.Collectors.toMap(line -> line.productId(), line -> line));

        SalesOrder order = new SalesOrder();
        // The database order id is the stable internal sequence. After the first
        // save we expose that same value as the short, human-readable order code.
        // A temporary unique value is required only while JPA obtains the id.
        order.setOrderCode("TEMP-" + System.nanoTime());
        order.setOrderDate(pricedAt);
        order.setCustomerName(blankToNull(request.getCustomerName()));
        order.setCustomerPhone(blankToNull(request.getCustomerPhone()));
        order.setUser(user);
        order.setOrderType(orderType);
        order.setPaymentMethod(paymentMethod);
        order.setNote(request.getNote());
        order.setStatus("PAID");
        order.setPaymentStatus("PAID");
        order.setPaidAt(pricedAt);

        List<SalesOrderItem> orderItems = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = catalog.get(entry.getKey());
            int quantity = entry.getValue();
            if (!product.getIsActive()) {
                throw new BadRequestException("Sản phẩm đã ngừng kinh doanh: " + product.getProductName());
            }
            int availableQuantity = inventoryBatchService.refreshProductBalances(product);
            if (availableQuantity < quantity) {
                throw new InsufficientStockException("Sản phẩm '" + product.getProductName()
                        + "' không đủ tồn khả dụng (còn " + availableQuantity + ", cần " + quantity + ")");
            }

            product.setStockQuantity(product.getStockQuantity() - quantity);
            productRepository.save(product);

            var price = prices.get(entry.getKey());
            BigDecimal unitPrice = price.originalUnitPrice();
            BigDecimal itemSubtotal = price.subTotal();

            SalesOrderItem orderItem = new SalesOrderItem();
            orderItem.setSalesOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(quantity);
            orderItem.setUnitPrice(unitPrice);
            orderItem.setNetUnitPrice(price.netUnitPrice());
            orderItem.setPromotionSnapshot(price.promotionSnapshot());
            orderItem.setSubtotal(itemSubtotal);
            orderItems.add(orderItem);
        }

        order.setSubtotal(quote.subtotal());
        order.setDiscountAmount(quote.discountAmount());
        order.setTotalAmount(quote.totalAmount());

        SalesOrder savedOrder = salesOrderRepository.save(order);
        savedOrder.setOrderCode(String.valueOf(savedOrder.getOrderId()));
        savedOrder = salesOrderRepository.save(savedOrder);
        List<SalesOrderItem> savedItems = salesOrderItemRepository.saveAll(orderItems);
        for (SalesOrderItem item : savedItems) {
            inventoryBatchService.allocate(item);
            saveMovement(item.getProduct(), savedOrder.getOrderId(), "SALE",
                    -item.getQuantity(), "Xuất kho tại quầy");
        }
        recordPayment(savedOrder, "PAYMENT", CASH.equals(paymentMethod)
                ? "POS-" + savedOrder.getOrderCode() : request.getPaymentReference().trim());
        dataSync.order(savedOrder, "ORDER_CREATED", "", savedOrder.getTotalAmount(), Map.of());
        return mapToResponse(savedOrder, savedItems);
    }

    @Override
    @Transactional
    public com.api.supermarket.dto.response.SalesOrderReturnResponse returnSalesOrder(Long orderId, ReturnOrderRequest request) {
        SalesOrder order = loadOrderForUpdate(orderId);
        ensurePosReturnAccess(order);
        if (request == null || isBlank(request.getRequestKey()) || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Cần mã yêu cầu và ít nhất một mặt hàng trả");
        }
        String requestKey = request.getRequestKey().trim();
        SalesOrderReturn existing = salesOrderReturnRepository.findByRequestKey(requestKey).orElse(null);
        if (existing != null) {
            if (!existing.getOrderId().equals(orderId)) {
                throw new BadRequestException("Mã yêu cầu trả hàng đã được dùng cho đơn khác");
            }
            ensureSameReturnRequest(existing, request);
            return mapReturnResponse(existing);
        }
        if (!Set.of("PAID", "PARTIALLY_RETURNED").contains(order.getStatus())
                || !Set.of("PAID", "PARTIALLY_REFUNDED", "REFUND_PENDING").contains(order.getPaymentStatus())) {
            throw new BadRequestException("Chỉ hóa đơn POS đã thanh toán và còn hàng chưa trả mới được nhận trả");
        }

        List<SalesOrderItem> orderItems = new ArrayList<>(salesOrderItemRepository.findBySalesOrder_OrderId(orderId));
        orderItems.sort(Comparator.comparing(SalesOrderItem::getOrderItemId));
        Map<Long, SalesOrderItem> itemsById = new java.util.HashMap<>();
        orderItems.forEach(item -> itemsById.put(item.getOrderItemId(), item));
        Map<Long, BigDecimal> offsets = new java.util.HashMap<>();
        BigDecimal grossSubtotal = BigDecimal.ZERO;
        for (SalesOrderItem item : orderItems) {
            offsets.put(item.getOrderItemId(), grossSubtotal);
            try {
                grossSubtotal = grossSubtotal.add(com.api.supermarket.domain.ReturnPolicy.refundAmount(
                        item.getQuantity(), 0, item.getQuantity(), item.getUnitPrice()));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException(ex.getMessage());
            }
        }
        Set<Long> requestedItemIds = new java.util.HashSet<>();
        BigDecimal refundAmount = BigDecimal.ZERO;
        for (ReturnItemRequest itemRequest : request.getItems()) {
            if (itemRequest == null || itemRequest.getOrderItemId() == null || itemRequest.getQuantity() == null
                    || itemRequest.getQuantity() <= 0 || itemRequest.getRestock() == null || isBlank(itemRequest.getReason())
                    || !requestedItemIds.add(itemRequest.getOrderItemId())) {
                throw new BadRequestException("Mặt hàng trả phải có mã, số lượng dương, tình trạng, lý do và không trùng");
            }
            SalesOrderItem orderItem = itemsById.get(itemRequest.getOrderItemId());
            if (orderItem == null) {
                throw new BadRequestException("Mặt hàng không thuộc đơn hàng này");
            }
            int alreadyReturned = orderItem.getReturnedQuantity() == null ? 0 : orderItem.getReturnedQuantity();
            try {
                BigDecimal amount = orderItem.getNetUnitPrice() != null
                        ? com.api.supermarket.domain.ReturnPolicy.refundAmount(orderItem.getQuantity(), alreadyReturned,
                                itemRequest.getQuantity(), orderItem.getNetUnitPrice())
                        : com.api.supermarket.domain.ReturnPolicy.refundAmount(
                                orderItem.getQuantity(), alreadyReturned, itemRequest.getQuantity(), orderItem.getUnitPrice(),
                                offsets.get(orderItem.getOrderItemId()), grossSubtotal, order.getTotalAmount());
                refundAmount = refundAmount.add(amount);
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException(ex.getMessage());
            }
        }

        ensureRefundBudget(order, refundAmount, true);

        SalesOrderReturn salesReturn = new SalesOrderReturn();
        salesReturn.setOrderId(orderId);
        salesReturn.setRequestKey(requestKey);
        salesReturn.setCreatedBy(SecurityContextHolder.getContext().getAuthentication().getName());
        salesReturn.setRefundStatus("NO_REFUND");
        List<ReturnItemData> returnItems = new ArrayList<>();
        List<ReturnItemRequest> sortedRequests = request.getItems().stream()
                .sorted(java.util.Comparator.comparing(item ->
                        itemsById.get(item.getOrderItemId()).getProduct().getProductId())).toList();
        for (ReturnItemRequest itemRequest : sortedRequests) {
            SalesOrderItem orderItem = itemsById.get(itemRequest.getOrderItemId());
            Product product = productRepository.findByIdForUpdate(orderItem.getProduct().getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm trả về"));
            int quantity = itemRequest.getQuantity();
            boolean restock = itemRequest.getRestock();
            inventoryBatchService.refreshProductBalances(product);
            returnItems.addAll(inventoryBatchService.receiveReturn(orderItem, quantity, restock,
                    itemRequest.getBatchId(), itemRequest.getReason().trim()));
            if (restock) {
                product.setStockQuantity(Math.addExact(product.getStockQuantity(), quantity));
            } else {
                product.setDamagedQuantity(Math.addExact(product.getDamagedQuantity(), quantity));
            }
            productRepository.save(product);
            saveMovement(product, orderId, restock ? "RETURN" : "RETURN_DAMAGED", quantity,
                    itemRequest.getReason().trim());
            int returnedBefore = orderItem.getReturnedQuantity() == null ? 0 : orderItem.getReturnedQuantity();
            orderItem.setReturnedQuantity(returnedBefore + quantity);

        }
        salesReturn.setRefundAmount(refundAmount);
        salesReturn.setItems(returnItems);
        salesReturn.setRefundStatus(refundAmount.signum() > 0 ? "REFUND_PENDING" : "NO_REFUND");
        SalesOrderReturn savedReturn = salesOrderReturnRepository.save(salesReturn);
        salesOrderItemRepository.saveAll(orderItems);
        boolean fullyReturned = orderItems.stream().allMatch(item -> item.getReturnedQuantity() != null
                && item.getReturnedQuantity().equals(item.getQuantity()));
        order.setStatus(fullyReturned ? "RETURNED" : "PARTIALLY_RETURNED");
        if (refundAmount.signum() > 0) {
            order.setPaymentStatus("REFUND_PENDING");
        }
        salesOrderRepository.save(order);
        dataSync.order(order, "RETURN_RECEIVED", savedReturn.getReturnId().toString(), refundAmount,
                Map.of("returnId", savedReturn.getReturnId(), "refundStatus", savedReturn.getRefundStatus()));
        return mapReturnResponse(savedReturn);
    }

    @Override
    @Transactional(readOnly = true)
    public SalesOrderResponse getSalesOrderById(Long orderId) {
        SalesOrder order = loadOrder(orderId);
        ensureOrderAccess(order);
        return mapToResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public SalesOrderResponse getSalesOrderCode(String orderCode) {
        SalesOrder order = salesOrderRepository.findByOrderCodeIgnoreCase(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn với mã: " + orderCode));
        ensureOrderAccess(order);
        return mapToResponse(order);
    }

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "orderId", "orderCode", "orderDate", "subtotal", "totalAmount", "status", "createdAt");

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SalesOrderResponse> getAllSalesOrders() {
        return filterSalesOrders(0, 10, "createdAt", "desc", null, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SalesOrderResponse> filterSalesOrders(int page, int size, String sortBy, String sortDir,
            String status, String paymentMethod, String searchTerm) {
        PaginationValidator.validate(page, size, sortBy, sortDir, ALLOWED_SORT_FIELDS);
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<SalesOrder> orderPage = salesOrderRepository.filterSalesOrder(
                isBlank(searchTerm) ? null : searchTerm.trim(), isBlank(status) ? null : normalize(status), IN_STORE,
                isBlank(paymentMethod) ? null : normalize(paymentMethod), null, null, pageable);
        List<SalesOrderResponse> content = orderPage.getContent().stream().map(this::mapToResponse).toList();
        return new PageResponse<>(content, orderPage.getNumber(), orderPage.getSize(), orderPage.getTotalElements(),
                orderPage.getTotalPages(), orderPage.isLast());
    }

    private SalesOrder loadOrder(Long orderId) {
        return salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn với ID: " + orderId));
    }

    private SalesOrder loadOrderForUpdate(Long orderId) {
        return salesOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + orderId));
    }

    private void ensureOrderAccess(SalesOrder order) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean internalUser = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> Set.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_STAFF").contains(a.getAuthority()));
        if (!internalUser) {
            throw new org.springframework.security.access.AccessDeniedException("Không có quyền truy cập đơn hàng");
        }
    }

    private boolean isStaff() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> Set.of("ROLE_ADMIN", "ROLE_STAFF").contains(a.getAuthority()));
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUserName(authentication.getName()).orElse(null);
    }

    private void validateOrderInput(String paymentMethod, SalesOrderRequest request) {
        if (!Set.of(CASH, BANK_TRANSFER).contains(paymentMethod)) {
            throw new BadRequestException("Phương thức thanh toán không được hỗ trợ");
        }
        if (BANK_TRANSFER.equals(paymentMethod)
                && isBlank(request.getPaymentReference())) {
            throw new BadRequestException("Thanh toán chuyển khoản tại quầy cần mã giao dịch đã xác nhận thành công");
        }
    }

    private Map<Long, Integer> mergeQuantities(List<OrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BadRequestException("Đơn hàng phải có ít nhất một sản phẩm");
        }
        Map<Long, Integer> quantities = new TreeMap<>();
        for (OrderItemRequest item : items) {
            if (item == null || item.getProductId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BadRequestException("Sản phẩm và số lượng trong đơn hàng phải hợp lệ");
            }
            try {
                quantities.merge(item.getProductId(), item.getQuantity(), Math::addExact);
            } catch (ArithmeticException ex) {
                throw new BadRequestException("Số lượng sản phẩm vượt giới hạn cho phép");
            }
        }
        return quantities;
    }

    @Override
    @Transactional
    public com.api.supermarket.dto.response.SalesOrderReturnResponse confirmReturnRefund(
            Long returnId, PaymentConfirmationRequest request) {
        SalesOrderReturn originalReturn = salesOrderReturnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu trả hàng"));
        SalesOrder order = loadOrderForUpdate(originalReturn.getOrderId());
        ensurePosReturnAccess(order);
        SalesOrderReturn salesReturn = salesOrderReturnRepository.findByIdForUpdate(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu trả hàng"));
        if (request == null || isBlank(request.getReference()) || request.getAmount() == null
                || request.getAmount().compareTo(salesReturn.getRefundAmount()) != 0) {
            throw new BadRequestException("Số tiền hoàn phải khớp số tiền của phiếu trả hàng");
        }
        if ("REFUNDED".equals(salesReturn.getRefundStatus())) {
            ensureMatchingReturnRefund(salesReturn, request);
            return mapReturnResponse(salesReturn);
        }
        if (!"REFUND_PENDING".equals(salesReturn.getRefundStatus())) {
            throw new BadRequestException("Phiếu trả hàng này không có khoản tiền cần hoàn");
        }
        ensureRefundBudget(order, salesReturn.getRefundAmount(), false);
        salesReturn.setRefundStatus("REFUNDED");
        salesReturn.setRefundedAt(LocalDateTime.now());
        recordPayment(order, "REFUND", request.getReference().trim(), returnId);
        salesOrderReturnRepository.save(salesReturn);

        BigDecimal refunded = salesOrderReturnRepository.sumRefundedByOrderId(order.getOrderId());
        boolean pending = salesOrderReturnRepository.existsByOrderIdAndRefundStatusIn(order.getOrderId(),
                List.of("REFUND_PENDING"));
        if (pending) {
            order.setPaymentStatus("REFUND_PENDING");
        } else if (refunded.compareTo(order.getTotalAmount()) >= 0) {
            order.setPaymentStatus("REFUNDED");
        } else if (refunded.signum() > 0) {
            order.setPaymentStatus("PARTIALLY_REFUNDED");
        }
        salesOrderRepository.save(order);
        dataSync.order(order, "RETURN_REFUND_CONFIRMED", returnId.toString(), BigDecimal.ZERO,
                Map.of("returnId", returnId));
        return mapReturnResponse(salesReturn);
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.api.supermarket.dto.response.SalesOrderReturnResponse> getReturns(Long orderId) {
        ensureOrderAccess(loadOrder(orderId));
        return salesOrderReturnRepository.findByOrderIdOrderByReturnIdAsc(orderId).stream()
                .map(this::mapReturnResponse).toList();
    }

    private com.api.supermarket.dto.response.SalesOrderReturnResponse mapReturnResponse(SalesOrderReturn salesReturn) {
        Map<Long, SalesOrderItem> orderItems = salesOrderItemRepository.findAllById(
                salesReturn.getItems().stream().map(ReturnItemData::orderItemId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(SalesOrderItem::getOrderItemId, item -> item));
        List<com.api.supermarket.dto.response.SalesOrderReturnResponse.Item> items = salesReturn.getItems().stream()
                .map(item -> {
                    SalesOrderItem orderItem = orderItems.get(item.orderItemId());
                    if (orderItem == null) throw new BadRequestException("Thiếu dòng hàng của phiếu trả");
                    return new com.api.supermarket.dto.response.SalesOrderReturnResponse.Item(item.orderItemId(),
                            orderItem.getProduct().getProductId(), item.batchId(), orderItem.getProduct().getProductName(),
                            item.quantity(), item.restock(), item.reason());
                }).toList();
        return new com.api.supermarket.dto.response.SalesOrderReturnResponse(salesReturn.getReturnId(),
                salesReturn.getOrderId(), salesReturn.getRequestKey(), salesReturn.getRefundAmount(),
                salesReturn.getRefundStatus(), salesReturn.getCreatedAt(), items);
    }

    private void ensureSameReturnRequest(SalesOrderReturn salesReturn, ReturnOrderRequest request) {
        List<ReturnItemData> recorded = salesReturn.getItems();
        Map<Long, ReturnItemRequest> incoming = new java.util.HashMap<>();
        for (ReturnItemRequest item : request.getItems()) {
            if (item == null || item.getOrderItemId() == null || item.getQuantity() == null
                    || item.getRestock() == null || isBlank(item.getReason())
                    || incoming.put(item.getOrderItemId(), item) != null) {
                throw new BadRequestException("Yêu cầu trả hàng lặp không hợp lệ");
            }
        }
        Map<Long, List<ReturnItemData>> byItem = recorded.stream()
                .collect(java.util.stream.Collectors.groupingBy(ReturnItemData::orderItemId));
        if (!incoming.keySet().equals(byItem.keySet())) {
            throw new BadRequestException("Mã yêu cầu đã được dùng với nội dung trả hàng khác");
        }
        for (Map.Entry<Long, List<ReturnItemData>> entry : byItem.entrySet()) {
            ReturnItemRequest candidate = incoming.get(entry.getKey());
            long quantity = entry.getValue().stream().mapToLong(ReturnItemData::quantity).sum();
            if (candidate.getQuantity() <= 0 || candidate.getQuantity().longValue() != quantity
                    || entry.getValue().stream().anyMatch(item ->
                        (candidate.getBatchId() != null && !candidate.getBatchId().equals(item.batchId()))
                        || candidate.getRestock() != item.restock()
                        || !candidate.getReason().trim().equals(item.reason()))) {
                throw new BadRequestException("Mã yêu cầu đã được dùng với nội dung trả hàng khác");
            }
        }
    }

    private void ensurePosReturnAccess(SalesOrder order) {
        if (!isStaff()) {
            throw new org.springframework.security.access.AccessDeniedException("Chỉ thu ngân hoặc quản trị viên được nhận trả/hoàn tiền");
        }
        if (!IN_STORE.equals(order.getOrderType())) {
            throw new BadRequestException("Chỉ xử lý trả hàng và hoàn phiếu cho hóa đơn POS");
        }
    }

    private void ensureRefundBudget(SalesOrder order, BigDecimal amount, boolean reservePending) {
        if (order.getTotalAmount() == null || order.getTotalAmount().signum() < 0 || amount == null || amount.signum() < 0) {
            throw new BadRequestException("Số tiền hóa đơn/phiếu trả không hợp lệ; cần đối soát trước khi hoàn");
        }
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;
        Map<Long, BigDecimal> refundsByReceipt = new java.util.HashMap<>();
        for (PaymentRecord payment : paymentRecordRepository.findByOrderIdOrderByPaymentRecordIdAsc(order.getOrderId())) {
            if (payment.getAmount() == null || payment.getAmount().signum() < 0) {
                throw new BadRequestException("Biên nhận có số tiền không hợp lệ; cần đối soát trước khi hoàn");
            }
            if ("PAYMENT".equals(payment.getEventType())) collected = collected.add(payment.getAmount());
            else if ("REFUND".equals(payment.getEventType())) {
                refunded = refunded.add(payment.getAmount());
                if (payment.getReturnId() != null) refundsByReceipt.merge(payment.getReturnId(), payment.getAmount(), BigDecimal::add);
            }
        }
        if (reservePending && collected.compareTo(order.getTotalAmount()) < 0) {
            throw new BadRequestException("Hóa đơn chưa có đủ biên nhận thu tiền; cần đối soát trước khi nhận trả");
        }
        BigDecimal pending = BigDecimal.ZERO;
        for (SalesOrderReturn receipt : salesOrderReturnRepository.findByOrderIdOrderByReturnIdAsc(order.getOrderId())) {
            if ("REFUNDED".equals(receipt.getRefundStatus())
                    && refundsByReceipt.getOrDefault(receipt.getReturnId(), BigDecimal.ZERO).compareTo(receipt.getRefundAmount()) != 0) {
                throw new BadRequestException("Phiếu đã hoàn thiếu biên nhận khớp tiền; cần đối soát trước khi hoàn thêm");
            } else if (reservePending && "REFUND_PENDING".equals(receipt.getRefundStatus())) pending = pending.add(receipt.getRefundAmount());
        }
        BigDecimal available = collected.min(order.getTotalAmount()).subtract(refunded).subtract(pending);
        if (amount.compareTo(available) > 0) {
            throw new BadRequestException("Tiền hoàn vượt số đã thu còn có thể hoàn; cần đối soát biên nhận và các phiếu đang chờ");
        }
    }

    private void validatePaymentConfirmation(SalesOrder order, PaymentConfirmationRequest request) {
        if (request == null || isBlank(request.getReference()) || request.getAmount() == null
                || request.getAmount().compareTo(order.getTotalAmount()) != 0) {
            throw new BadRequestException("Cần mã giao dịch/biên nhận và số tiền bằng tổng tiền của đơn hàng");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.api.supermarket.dto.response.PaymentRecordResponse> getPaymentRecords(Long orderId) {
        ensureOrderAccess(loadOrder(orderId));
        return paymentRecordRepository.findByOrderIdOrderByPaymentRecordIdAsc(orderId).stream()
                .map(p -> new com.api.supermarket.dto.response.PaymentRecordResponse(p.getPaymentRecordId(), p.getOrderId(), p.getReturnId(),
                        p.getEventType(), p.getReference(), p.getAmount(), p.getPaymentMethod(), p.getConfirmedBy(), p.getCreatedAt()))
                .toList();
    }

    private void ensureMatchingReturnRefund(SalesOrderReturn salesReturn, PaymentConfirmationRequest request) {
        PaymentRecord payment = paymentRecordRepository.findByReturnIdAndEventType(salesReturn.getReturnId(), "REFUND")
                .orElseThrow(() -> new BadRequestException("Phiếu trả hàng chưa có biên nhận hoàn tiền"));
        if (!payment.getReference().equals(request.getReference().trim())) {
            throw new BadRequestException("Phiếu trả hàng đã hoàn bằng mã giao dịch khác");
        }
    }

    private void recordPayment(SalesOrder order, String type, String reference) {
        recordPayment(order, type, reference, null);
    }

    private void recordPayment(SalesOrder order, String type, String reference, Long returnId) {
        if (paymentRecordRepository.existsByReference(reference)) {
            throw new BadRequestException("Mã giao dịch/biên nhận đã được sử dụng");
        }
        PaymentRecord payment = new PaymentRecord();
        payment.setOrderId(order.getOrderId());
        payment.setReturnId(returnId);
        payment.setEventType(type);
        payment.setReference(reference);
        payment.setAmount(returnId == null ? order.getTotalAmount()
                : salesOrderReturnRepository.findById(returnId)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu trả hàng"))
                        .getRefundAmount());
        payment.setPaymentMethod(order.getPaymentMethod());
        User user = currentUser();
        payment.setConfirmedBy(user == null ? null : user.getUserId());
        dataSync.payment(paymentRecordRepository.save(payment));
    }

    private void saveMovement(Product product, Long orderId, String type, int quantity, String note) {
        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setOrderId(orderId);
        movement.setMovementType(type);
        movement.setQuantity(quantity);
        movement.setNote(note);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        movement.setPerformedBy(authentication == null ? "SYSTEM" : authentication.getName());
        dataSync.movement(stockMovementRepository.save(movement));
    }

    private SalesOrderResponse mapToResponse(SalesOrder order) {
        List<SalesOrderItem> items = salesOrderItemRepository.findBySalesOrder_OrderId(order.getOrderId());
        return mapToResponse(order, items);
    }

    private SalesOrderResponse mapToResponse(SalesOrder order, List<SalesOrderItem> items) {
        List<OrderItemResponse> itemResponses = items.stream().map(item -> {
            Product p = item.getProduct();
            ProductResponse productResponse = new ProductResponse(p.getProductId(), p.getProductName(), p.getSkuCode(),
                    p.getPrice(), p.getStockQuantity(), p.getReorderPoint(), p.getImageUrl(),
                    p.getIsActive(), p.getCategoryName(), p.getCreateAt());
            productResponse.setDamagedQuantity(p.getDamagedQuantity());
            productResponse.setAvailableQuantity(p.getIsActive() ? inventoryBatchService.usableQuantity(p.getProductId()) : 0);
            OrderItemResponse itemResponse = new OrderItemResponse(item.getOrderItemId(), productResponse, item.getQuantity(), item.getUnitPrice(),
                    item.getSubtotal());
            itemResponse.setNetUnitPrice(item.getNetUnitPrice());
            itemResponse.setPromotionSnapshot(item.getPromotionSnapshot());
            itemResponse.setReturnedQuantity(item.getReturnedQuantity());
            itemResponse.setBatchAllocations(inventoryBatchService.getAllocations(item));
            return itemResponse;
        }).toList();

        User user = order.getUser();
        return new SalesOrderResponse(order.getOrderId(), order.getOrderCode(),
                order.getCustomerName() == null ? "Khách vãng lai" : order.getCustomerName(),
                order.getCustomerPhone(), user == null ? null : user.getUserId(),
                user == null ? null : user.getFullName(), order.getOrderType(), order.getPaymentMethod(), order.getStatus(),
                order.getPaymentStatus(), order.getNote(), order.getSubtotal(), order.getDiscountAmount(), order.getTotalAmount(), order.getCreatedAt(),
                order.getPaidAt(), itemResponses);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String blankToNull(String value) { return isBlank(value) ? null : value.trim(); }
}
