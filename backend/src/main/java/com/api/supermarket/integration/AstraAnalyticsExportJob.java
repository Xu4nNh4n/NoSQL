package com.api.supermarket.integration;

import com.api.supermarket.exception.BadRequestException;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "astra.enabled", havingValue = "true")
public class AstraAnalyticsExportJob {
    private static final Logger log = LoggerFactory.getLogger(AstraAnalyticsExportJob.class);
    private final AstraAnalyticsService analytics;

    public AstraAnalyticsExportJob(AstraAnalyticsService analytics) { this.analytics = analytics; }

    @Scheduled(fixedDelayString = "${integration.analytics-export.interval-ms:60000}", initialDelay = 30000)
    public void exportRecentDays() {
        LocalDate today = ReportTime.today();
        for (LocalDate date : new LocalDate[] {today.minusDays(1), today}) {
            try {
                var result = analytics.export(date);
                log.debug("Đã xuất {} sự kiện Cassandra ngày {} cho Drill", result.get("rows"), date);
            } catch (BadRequestException ex) {
                log.debug("Chưa chuẩn bị được phân vùng Cassandra ngày {}", date);
            } catch (RuntimeException ex) {
                log.warn("Chưa xuất được Cassandra ngày {}, loại lỗi={}; sẽ thử lại", date, ex.getClass().getSimpleName());
            }
        }
    }
}
