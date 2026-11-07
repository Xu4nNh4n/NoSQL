package com.api.supermarket.sync;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.api.supermarket.common.AppTime;

/** Mỗi phút thử ghi lại các việc còn PENDING (NFR-05: trễ tối đa khoảng 60 giây). */
@Component
@ConditionalOnProperty(name = "app.sync.enabled", havingValue = "true")
public class SyncJob {
    private static final Logger log = LoggerFactory.getLogger(SyncJob.class);
    private static final int BATCH = 200;

    private final SyncService service;
    private final AppTime time;
    private final Duration minAge;

    public SyncJob(SyncService service, AppTime time, @Value("${app.sync.min-age-seconds:30}") long minAgeSeconds) {
        this.service = service;
        this.time = time;
        this.minAge = Duration.ofSeconds(minAgeSeconds);
    }

    @Scheduled(fixedDelayString = "${app.sync.interval-ms:60000}", initialDelayString = "${app.sync.interval-ms:60000}")
    public void run() {
        try {
            int done = service.processPending(time.now().minus(minAge), BATCH);
            if (done > 0) {
                log.info("Job đồng bộ: ghi xong {} việc", done);
            }
        } catch (RuntimeException ex) {
            log.warn("Job đồng bộ không chạy được (MongoDB?): {}", ex.toString());
        }
    }
}
