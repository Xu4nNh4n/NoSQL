package com.api.supermarket.graph;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.api.supermarket.catalog.product.Product;
import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.common.DatastoreUnavailableException;

/** Truy vấn đồ thị (AP-20, AP-21). Mọi Cypher dùng tham số, không ghép chuỗi từ dữ liệu người dùng (NFR-11). */
@Service
public class GraphQueryService {
    private static final Logger log = LoggerFactory.getLogger(GraphQueryService.class);

    static final String TRACE = """
            MATCH (b:Batch {code: $code})<-[t:TOOK_FROM]-(o:Order)
            RETURN o.code AS orderCode, o.at AS at, t.qty AS qty
            ORDER BY at""";
    static final String ALSO_BOUGHT = """
            MATCH (p:Product {id: $id})<-[r1:CONTAINS]-(o:Order)-[r2:CONTAINS]->(x:Product)
            WHERE x <> p AND NOT coalesce(r1.gift, false) AND NOT coalesce(r2.gift, false)
            RETURN x.id AS id, x.name AS name, count(DISTINCT o) AS times
            ORDER BY times DESC, name LIMIT $limit""";

    public record TracedOrder(String orderCode, Instant orderedAt, long quantity) {
    }

    public record Suggestion(String productId, String sku, String name, long times) {
    }

    public record Suggestions(boolean available, List<Suggestion> items) {
    }

    private final ObjectProvider<Driver> driver;
    private final ProductRepository products;

    public GraphQueryService(ObjectProvider<Driver> driver, ProductRepository products) {
        this.driver = driver;
        this.products = products;
    }

    public List<TracedOrder> trace(String batchCode) {
        Driver neo4j = driver.getIfAvailable();
        if (neo4j == null) {
            throw new DatastoreUnavailableException("neo4j", new IllegalStateException("not configured"));
        }
        try (Session session = neo4j.session()) {
            return session.executeRead(tx -> {
                List<TracedOrder> rows = new ArrayList<>();
                for (Record r : tx.run(TRACE, Map.of("code", batchCode)).list()) {
                    Value at = r.get("at");
                    rows.add(new TracedOrder(r.get("orderCode").asString(null),
                            at.isNull() ? null : at.asZonedDateTime().toInstant(), r.get("qty").asLong(0)));
                }
                return rows;
            });
        } catch (RuntimeException ex) {
            throw new DatastoreUnavailableException("neo4j", ex);
        }
    }

    /** Gợi ý mua kèm cho quầy; Neo4j lỗi thì trả rỗng để không chặn việc bán. */
    public Suggestions alsoBought(String productId, int limit) {
        Driver neo4j = driver.getIfAvailable();
        if (neo4j == null) {
            return new Suggestions(false, List.of());
        }
        List<Suggestion> raw = new ArrayList<>();
        try (Session session = neo4j.session()) {
            // lấy dư để còn đủ sau khi bỏ sản phẩm ngừng bán
            raw = session.executeRead(tx -> tx.run(ALSO_BOUGHT, Map.of("id", productId, "limit", (long) limit * 2)).list()
                    .stream().map(r -> new Suggestion(r.get("id").asString(), null, r.get("name").asString(null),
                            r.get("times").asLong()))
                    .toList());
        } catch (RuntimeException ex) {
            log.warn("Không lấy được gợi ý mua kèm: {}", ex.toString());
            return new Suggestions(false, List.of());
        }
        return new Suggestions(true, onlyActive(raw, limit));
    }

    List<Suggestion> onlyActive(List<Suggestion> raw, int limit) {
        List<Suggestion> result = new ArrayList<>();
        for (Suggestion s : raw) {
            Product p = products.findById(s.productId()).orElse(null);
            if (p != null && p.isActive()) {
                result.add(new Suggestion(p.getId(), p.getSku(), p.getName(), s.times()));
            }
            if (result.size() == limit) {
                break;
            }
        }
        return result;
    }
}
