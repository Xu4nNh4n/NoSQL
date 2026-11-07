package com.api.supermarket.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.api.supermarket.catalog.product.ProductService;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.RedisSafe;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.graph.GraphQueryService;
import com.api.supermarket.inventory.BatchRepository;
import com.api.supermarket.sales.TopSalesService;

import tools.jackson.databind.json.JsonMapper;

class StatsTest {
    private static final AppTime TIME = new AppTime(Clock.fixed(Instant.parse("2026-11-07T03:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh")));

    private static Decimal128 d(long v) {
        return new Decimal128(BigDecimal.valueOf(v));
    }

    @Test
    void mapsFacetResult() {
        Date day = Date.from(LocalDate.of(2026, 11, 6).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Document facet = new Document("daily", List.of(new Document("_id", day).append("orders", 3).append("gross", d(300_000))
                .append("discount", d(20_000)).append("revenue", d(280_000)).append("refunds", d(30_000))))
                .append("byCategory", List.of(new Document("_id", "c1").append("quantity", 7).append("revenue", d(280_000))
                        .append("category", List.of(new Document("name", "Sữa")))))
                .append("topProducts", List.of(new Document("_id", "p1").append("sku", "SUA").append("name", "Sữa")
                        .append("quantity", 7).append("revenue", d(280_000))))
                .append("totals", List.of(new Document("_id", null).append("orders", 3).append("gross", d(300_000))
                        .append("discount", d(20_000)).append("revenue", d(280_000)).append("refunds", d(30_000))));

        StatsService.SalesStats s = StatsService.map(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7), facet);
        assertEquals(LocalDate.of(2026, 11, 6), s.daily().get(0).day());
        assertEquals(BigDecimal.valueOf(250_000), s.daily().get(0).netRevenue());
        assertEquals("Sữa", s.byCategory().get(0).categoryName());
        assertEquals(7, s.topProducts().get(0).quantity());
        assertEquals(3, s.totals().orders());
        assertEquals(BigDecimal.valueOf(250_000), s.totals().netRevenue());
    }

    @Test
    void emptyPeriodGivesZeros() {
        StatsService.SalesStats s = StatsService.map(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7),
                new Document("daily", List.of()).append("byCategory", List.of()).append("topProducts", List.of())
                        .append("totals", List.of()));
        assertEquals(0, s.totals().orders());
        assertEquals(BigDecimal.ZERO, s.totals().netRevenue());
    }

    @Test
    void csvHasBomHeaderRowsAndTotal() {
        StatsService.SalesStats s = new StatsService.SalesStats(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 6),
                new StatsService.Totals(1, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.valueOf(9), BigDecimal.ZERO, BigDecimal.valueOf(9)),
                List.of(new StatsService.DayRow(LocalDate.of(2026, 11, 6), 1, BigDecimal.TEN, BigDecimal.ONE,
                        BigDecimal.valueOf(9), BigDecimal.ZERO, BigDecimal.valueOf(9))), List.of(), List.of());
        String csv = new String(StatsService.toCsv(s), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("﻿Ngày,Số đơn"));
        assertTrue(csv.contains("2026-11-06,1,10,1,9,0,9\r\n"));
        assertTrue(csv.endsWith("Tổng,1,10,1,9,0,9\r\n"));
    }

    @Test
    void rangeValidation() {
        StatsService service = new StatsService(mock(MongoTemplate.class), TIME);
        assertThrows(BadRequestException.class, () -> service.sales(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 1)));
        assertThrows(BadRequestException.class, () -> service.sales(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 11, 7)));
    }

    @Test
    void pipelineStartsWithMatchThenFacet() {
        List<Document> p = StatsService.pipeline(Instant.EPOCH, Instant.EPOCH.plusSeconds(86_400));
        assertEquals(2, p.size());
        assertTrue(p.get(0).containsKey("$match"));
        Document facet = p.get(1).get("$facet", Document.class);
        assertEquals(List.of("daily", "byCategory", "topProducts", "totals"), List.copyOf(facet.keySet()));
    }

    @Test
    void panelRunsPartsInParallel() {
        ProductService products = mock(ProductService.class);
        BatchRepository batches = mock(BatchRepository.class);
        GraphQueryService graph = mock(GraphQueryService.class);
        TopSalesService top = mock(TopSalesService.class);
        when(products.get("p1")).thenAnswer(inv -> sleepThen(200, null));
        when(batches.findAvailableForFefo(eq("p1"), any())).thenAnswer(inv -> sleepThen(200, List.of()));
        when(graph.alsoBought(eq("p1"), anyInt())).thenAnswer(inv -> sleepThen(200, new GraphQueryService.Suggestions(true, List.of())));
        when(top.today(anyInt())).thenAnswer(inv -> sleepThen(200, null));

        PosPanelService service = new PosPanelService(products, batches, graph, top, TIME);
        PosPanelService.Panel panel = service.panel("p1");
        assertEquals(4, panel.timings().size());
        assertTrue(panel.sequentialMillis() >= 780, "tổng tuần tự ~800ms: " + panel.sequentialMillis());
        assertTrue(panel.parallelMillis() < 600, "song song phải gần 200ms: " + panel.parallelMillis());
        service.destroy();
    }

    @Test
    void expiringSoonIsCachedForADay() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        RedisSafe redis = mock(RedisSafe.class);
        when(redis.get(anyString())).thenReturn(java.util.Optional.empty());
        ExpiringSoonService service = new ExpiringSoonService(mongo, redis, JsonMapper.builder().build(), TIME);
        assertFalse(service.expiringSoon() == null);
        verify(redis).set(eq(ExpiringSoonService.KEY), anyString(), eq(ExpiringSoonService.TTL));
    }

    private static <T> T sleepThen(long ms, T value) throws InterruptedException {
        Thread.sleep(ms);
        return value;
    }
}
