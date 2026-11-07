package com.api.supermarket.shift;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Bản chốt ca (collection {@code shift_closings}): mỗi thu ngân mỗi ngày một bản; ADMIN mở lại thì chốt lại được. */
@Data
@NoArgsConstructor
@Document(ShiftClosing.COLLECTION)
public class ShiftClosing {
    public static final String COLLECTION = "shift_closings";

    public enum Status {
        CLOSED, REOPENED
    }

    @Id
    private String id;
    private String cashierId;
    private String cashierName;
    private LocalDate day;
    private BigDecimal cashIn;
    private BigDecimal bankIn;
    private BigDecimal cashRefund;
    private BigDecimal bankRefund;
    private BigDecimal expectedCash;
    private BigDecimal countedCash;
    private BigDecimal difference;
    private int paymentCount;
    private int refundCount;
    private String note;
    private Status status;
    private Instant closedAt;
    private String reopenedBy;
    private Instant reopenedAt;
    @Version
    private Long version;
}
