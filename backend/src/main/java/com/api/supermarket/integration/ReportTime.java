package com.api.supermarket.integration;

import java.time.*;
import java.time.format.DateTimeFormatter;

final class ReportTime {
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static LocalDate today() { return LocalDate.now(ZONE); }
    static String utcStart(LocalDate date) {
        return date.atStartOfDay(ZONE).withZoneSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    private ReportTime() { }
}
