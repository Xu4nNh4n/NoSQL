package com.api.supermarket.shift;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.api.supermarket.common.AppTime;
import com.api.supermarket.config.MongoIndexContributor;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.sales.LedgerPayment;
import com.api.supermarket.sales.PaymentLedger;
import com.api.supermarket.security.JwtPrincipal;
import com.api.supermarket.sync.SyncService;
import com.api.supermarket.user.UserFields;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Chốt ca thu ngân (FR-12, AP-15). Trước khi tính tổng, xử lý các việc đồng bộ còn chờ của thu ngân để
 * sổ thanh toán Cassandra không thiếu; rồi đọc partition {@code (cashier_id, day)} của
 * {@code payments_by_cashier_day}. Tiền mặt phải có = thu tiền mặt − hoàn tiền mặt.
 */
@Service
public class ShiftService implements MongoIndexContributor {
    public record CloseRequest(@NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 0) BigDecimal countedCash,
            @Size(max = 500) String note) {
    }

    public record ShiftSummary(String cashierId, LocalDate day, BigDecimal cashIn, BigDecimal bankIn,
            BigDecimal cashRefund, BigDecimal bankRefund, BigDecimal expectedCash, int paymentCount, int refundCount,
            ShiftClosing closing) {
    }

    private final PaymentLedger ledger;
    private final SyncService sync;
    private final MongoTemplate mongo;
    private final AppTime time;

    public ShiftService(PaymentLedger ledger, SyncService sync, MongoTemplate mongo, AppTime time) {
        this.ledger = ledger;
        this.sync = sync;
        this.mongo = mongo;
        this.time = time;
    }

    @Override
    public void ensureIndexes(MongoTemplate template) {
        template.indexOps(ShiftClosing.COLLECTION).createIndex(new Index().on("cashierId", Sort.Direction.ASC)
                .on("day", Sort.Direction.ASC).unique().named("cashier_day_unique"));
    }

    public ShiftSummary today(JwtPrincipal cashier) {
        return summary(cashier.userId(), time.today());
    }

    public ShiftSummary summary(String cashierId, LocalDate day) {
        sync.processPendingForOwner(cashierId);
        List<LedgerPayment> payments = ledger.byCashierDay(cashierId, day);
        BigDecimal cashIn = sum(payments, "PAYMENT", "CASH");
        BigDecimal bankIn = sum(payments, "PAYMENT", "BANK_TRANSFER");
        BigDecimal cashRefund = sum(payments, "REFUND", "CASH");
        BigDecimal bankRefund = sum(payments, "REFUND", "BANK_TRANSFER");
        int paymentCount = (int) payments.stream().filter(p -> "PAYMENT".equals(p.type())).count();
        return new ShiftSummary(cashierId, day, cashIn, bankIn, cashRefund, bankRefund, cashIn.subtract(cashRefund),
                paymentCount, payments.size() - paymentCount, find(cashierId, day).orElse(null));
    }

    public ShiftClosing close(JwtPrincipal cashier, CloseRequest req) {
        LocalDate day = time.today();
        ShiftSummary s = summary(cashier.userId(), day);
        ShiftClosing c = s.closing() != null ? s.closing() : new ShiftClosing();
        if (c.getStatus() == ShiftClosing.Status.CLOSED) {
            throw new BadRequestException("Ca hôm nay đã chốt; nhờ ADMIN mở lại nếu cần chốt lại");
        }
        c.setCashierId(cashier.userId());
        c.setCashierName(cashier.username());
        c.setDay(day);
        c.setCashIn(s.cashIn());
        c.setBankIn(s.bankIn());
        c.setCashRefund(s.cashRefund());
        c.setBankRefund(s.bankRefund());
        c.setExpectedCash(s.expectedCash());
        c.setCountedCash(req.countedCash());
        c.setDifference(req.countedCash().subtract(s.expectedCash()));
        c.setPaymentCount(s.paymentCount());
        c.setRefundCount(s.refundCount());
        c.setNote(UserFields.text(req.note()));
        c.setStatus(ShiftClosing.Status.CLOSED);
        c.setClosedAt(time.now());
        return mongo.save(c);
    }

    public List<ShiftClosing> list(LocalDate day) {
        return mongo.find(Query.query(Criteria.where("day").is(day)).with(Sort.by("cashierName")), ShiftClosing.class);
    }

    public ShiftClosing reopen(String id, JwtPrincipal admin) {
        ShiftClosing c = Optional.ofNullable(mongo.findById(id, ShiftClosing.class))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản chốt ca"));
        c.setStatus(ShiftClosing.Status.REOPENED);
        c.setReopenedBy(admin.username());
        c.setReopenedAt(time.now());
        return mongo.save(c);
    }

    private Optional<ShiftClosing> find(String cashierId, LocalDate day) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("cashierId").is(cashierId).and("day").is(day)),
                ShiftClosing.class));
    }

    private static BigDecimal sum(List<LedgerPayment> payments, String type, String method) {
        return payments.stream().filter(p -> type.equals(p.type()) && method.equals(p.method()))
                .map(LedgerPayment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
