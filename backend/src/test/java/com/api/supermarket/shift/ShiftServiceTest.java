package com.api.supermarket.shift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import com.api.supermarket.common.AppTime;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.sales.LedgerPayment;
import com.api.supermarket.sales.PaymentLedger;
import com.api.supermarket.security.JwtPrincipal;
import com.api.supermarket.shift.ShiftService.CloseRequest;
import com.api.supermarket.shift.ShiftService.ShiftSummary;
import com.api.supermarket.sync.SyncService;

class ShiftServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 7);
    private static final JwtPrincipal CASHIER = new JwtPrincipal("c1", "thungan01", "STAFF");

    private PaymentLedger ledger;
    private SyncService sync;
    private MongoTemplate mongo;
    private ShiftService service;

    private static LedgerPayment pay(String type, String method, long amount) {
        return new LedgerPayment(UUID.randomUUID(), null, "HD", type, method, BigDecimal.valueOf(amount), null, null, "c1", NOW);
    }

    @BeforeEach
    void setUp() {
        ledger = mock(PaymentLedger.class);
        sync = mock(SyncService.class);
        mongo = mock(MongoTemplate.class);
        service = new ShiftService(ledger, sync, mongo, new AppTime(Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"))));
        when(ledger.byCashierDay("c1", TODAY)).thenReturn(List.of(pay("PAYMENT", "CASH", 500_000),
                pay("PAYMENT", "CASH", 200_000), pay("PAYMENT", "BANK_TRANSFER", 300_000), pay("REFUND", "CASH", 50_000)));
        when(mongo.save(any(ShiftClosing.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void summarySyncsPendingFirstThenSumsLedger() {
        ShiftSummary s = service.today(CASHIER);
        InOrder order = inOrder(sync, ledger);
        order.verify(sync).processPendingForOwner("c1");
        order.verify(ledger).byCashierDay("c1", TODAY);
        assertEquals(BigDecimal.valueOf(700_000), s.cashIn());
        assertEquals(BigDecimal.valueOf(300_000), s.bankIn());
        assertEquals(BigDecimal.valueOf(50_000), s.cashRefund());
        assertEquals(BigDecimal.valueOf(650_000), s.expectedCash());
        assertEquals(3, s.paymentCount());
        assertEquals(1, s.refundCount());
    }

    @Test
    void closeComputesDifferenceAndRejectsSecondClose() {
        ShiftClosing c = service.close(CASHIER, new CloseRequest(BigDecimal.valueOf(640_000), " thiếu 10k "));
        assertEquals(BigDecimal.valueOf(-10_000), c.getDifference());
        assertEquals("thiếu 10k", c.getNote());
        assertEquals(ShiftClosing.Status.CLOSED, c.getStatus());

        when(mongo.findOne(any(Query.class), eq(ShiftClosing.class))).thenReturn(c);
        assertThrows(BadRequestException.class, () -> service.close(CASHIER, new CloseRequest(BigDecimal.ONE, null)));

        c.setStatus(ShiftClosing.Status.REOPENED);
        assertEquals(ShiftClosing.Status.CLOSED,
                service.close(CASHIER, new CloseRequest(BigDecimal.valueOf(650_000), null)).getStatus());
    }
}
