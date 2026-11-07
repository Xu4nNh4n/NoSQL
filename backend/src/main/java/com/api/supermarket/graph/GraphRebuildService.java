package com.api.supermarket.graph;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.api.supermarket.common.ConflictException;
import com.api.supermarket.common.DatastoreUnavailableException;
import com.api.supermarket.inventory.Batch;
import com.api.supermarket.inventory.InventoryEvents;
import com.api.supermarket.sales.SalesEvents;
import com.api.supermarket.sales.SalesOrder;

/**
 * Dựng lại toàn bộ đồ thị từ MongoDB (nguồn sự thật), dùng khi đồ thị lệch hoặc Aura bị xóa (NFR-10:
 * dưới 5 phút với 10 nghìn đơn). Đọc theo trang 500 bằng {@code _id > last} để không phải skip.
 */
@Service
public class GraphRebuildService {
    private static final Logger log = LoggerFactory.getLogger(GraphRebuildService.class);
    static final int PAGE = 500;

    public record RebuildResult(long deletedNodes, long batches, long orders, long millis) {
    }

    private final ObjectProvider<Driver> driver;
    private final GraphWriter writer;
    private final MongoTemplate mongo;
    private final AtomicBoolean running = new AtomicBoolean();

    public GraphRebuildService(ObjectProvider<Driver> driver, GraphWriter writer, MongoTemplate mongo) {
        this.driver = driver;
        this.writer = writer;
        this.mongo = mongo;
    }

    public RebuildResult rebuild() {
        Driver neo4j = driver.getIfAvailable();
        if (neo4j == null) {
            throw new DatastoreUnavailableException("neo4j", new IllegalStateException("not configured"));
        }
        if (!running.compareAndSet(false, true)) {
            throw new ConflictException("REBUILD_RUNNING", "Đồ thị đang được dựng lại");
        }
        long start = System.nanoTime();
        try {
            long deleted = clear(neo4j);
            long[] batches = {0};
            page(Batch.class, page -> {
                writer.writeBatches(neo4j, GraphRows.batches(InventoryEvents.payload(List.of(), page)));
                batches[0] += page.size();
            });
            long[] orders = {0};
            page(SalesOrder.class, page -> {
                GraphRows.OrderRows rows = GraphRows.OrderRows.empty();
                for (SalesOrder o : page) {
                    GraphRows.addOrder(SalesEvents.orderCreated(o).get("graph", Document.class), rows);
                }
                writer.writeOrders(neo4j, rows);
                orders[0] += page.size();
            });
            long millis = (System.nanoTime() - start) / 1_000_000;
            log.info("Dựng lại đồ thị: xóa {} node, {} lô, {} đơn trong {} ms", deleted, batches[0], orders[0], millis);
            return new RebuildResult(deleted, batches[0], orders[0], millis);
        } catch (DatastoreUnavailableException | ConflictException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new DatastoreUnavailableException("neo4j", ex);
        } finally {
            running.set(false);
        }
    }

    private long clear(Driver neo4j) {
        long total = 0;
        try (Session session = neo4j.session()) {
            while (true) {
                long n = session.executeWrite(tx -> tx.run("MATCH (n) WITH n LIMIT 5000 DETACH DELETE n RETURN count(*) AS c",
                        Map.of()).single().get("c").asLong());
                total += n;
                if (n == 0) {
                    return total;
                }
            }
        }
    }

    private <T> void page(Class<T> type, Consumer<List<T>> consumer) {
        ObjectId last = null;
        while (true) {
            Query q = last == null ? new Query() : Query.query(Criteria.where("_id").gt(last));
            List<T> page = mongo.find(q.with(Sort.by("_id")).limit(PAGE), type);
            if (page.isEmpty()) {
                return;
            }
            consumer.accept(page);
            last = new ObjectId(idOf(page.get(page.size() - 1)));
            if (page.size() < PAGE) {
                return;
            }
        }
    }

    private static String idOf(Object o) {
        if (o instanceof Batch b) {
            return b.getId();
        }
        return ((SalesOrder) o).getId();
    }
}
