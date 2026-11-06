package com.api.supermarket.service.Impl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.api.supermarket.domain.ReturnItemData;
import com.api.supermarket.dto.request.*;
import com.api.supermarket.entity.*;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.integration.DataSyncPublisher;
import com.api.supermarket.repository.*;
import com.api.supermarket.service.InventoryBatchService;

class SalesOrderServiceImplTest {
    private final SalesOrderRepository orders = mock(SalesOrderRepository.class);
    private final SalesOrderItemRepository items = mock(SalesOrderItemRepository.class);
    private final SalesOrderReturnRepository returns = mock(SalesOrderReturnRepository.class);
    private final InventoryBatchService inventory = mock(InventoryBatchService.class);
    private final PaymentRecordRepository payments = mock(PaymentRecordRepository.class);
    private final SalesOrderServiceImpl service = new SalesOrderServiceImpl(orders,
            mock(ProductRepository.class), items, mock(UserRepository.class), mock(StockMovementRepository.class),
            payments, returns, inventory, mock(DataSyncPublisher.class));
    @BeforeEach void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "staff", "", List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private ReturnOrderRequest request(int qty, Long batchId) {
        ReturnItemRequest line = new ReturnItemRequest();
        line.setOrderItemId(10L); line.setQuantity(qty); line.setBatchId(batchId);
        line.setRestock(true); line.setReason("Unopened");
        ReturnOrderRequest r = new ReturnOrderRequest();
        r.setRequestKey("return-1"); r.setItems(List.of(line)); return r;
    }
    private void existing(List<ReturnItemData> lines) {
        SalesOrder o = new SalesOrder(); o.setOrderId(1L);
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(o));
        SalesOrderReturn r = new SalesOrderReturn(); r.setReturnId(100L); r.setOrderId(1L);
        r.setItems(lines);
        when(returns.findByRequestKey("return-1")).thenReturn(Optional.of(r));
        Product p = new Product(); p.setProductId(1L); p.setProductName("Milk");
        SalesOrderItem i = new SalesOrderItem(); i.setOrderItemId(10L); i.setProduct(p);
        when(items.findAllById(any())).thenReturn(List.of(i));
    }
    @Test void retryAcceptsAutomaticallyResolvedLot() {
        existing(List.of(new ReturnItemData(10L, 7L, 3, true, "Unopened")));
        assertDoesNotThrow(() -> service.returnSalesOrder(1L, request(3, null)));
        verifyNoInteractions(inventory, payments);
        verify(returns, never()).save(any());
    }
    @Test void retryAcceptsOneItemSplitAcrossLots() {
        existing(List.of(new ReturnItemData(10L, 7L, 1, true, "Unopened"),
                new ReturnItemData(10L, 8L, 2, true, "Unopened")));
        assertEquals(2, service.returnSalesOrder(1L, request(3, null)).items().size());
        verifyNoInteractions(inventory);
    }
    @Test void retryRejectsDifferentQuantityOrExplicitLot() {
        existing(List.of(new ReturnItemData(10L, 7L, 3, true, "Unopened")));
        assertThrows(BadRequestException.class, () -> service.returnSalesOrder(1L, request(2, null)));
        assertThrows(BadRequestException.class, () -> service.returnSalesOrder(1L, request(3, 8L)));
        verifyNoInteractions(inventory);
    }
    @Test void onlineOrderIsRejected() {
        SalesOrderRequest r = new SalesOrderRequest(); r.setOrderType("ONLINE");
        assertThrows(BadRequestException.class, () -> service.createSalesOrder(r));
        verifyNoInteractions(orders, inventory, payments);
    }
}
