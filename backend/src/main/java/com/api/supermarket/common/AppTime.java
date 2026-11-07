package com.api.supermarket.common;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

/** Thời gian của ứng dụng theo một múi giờ thống nhất (mặc định Asia/Ho_Chi_Minh). */
@Component
public class AppTime {
    private final Clock clock;

    public AppTime(Clock clock) {
        this.clock = clock;
    }

    public ZoneId zone() {
        return clock.getZone();
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
