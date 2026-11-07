// 03-queries-basic.cypher – truy vấn cơ bản. Backend luôn truyền giá trị bằng tham số ($code, $id), không ghép chuỗi.
// Trong Neo4j Browser đặt tham số trước: :param code => 'L261101-0003'

// [CB-N1] Tìm sản phẩm theo SKU và các lô của nó
MATCH (p:Product {sku: 'SUA-VNM-1L'})<-[:OF_PRODUCT]-(b:Batch)
RETURN p.name, b.code, b.expiry ORDER BY b.expiry;

// [CB-N2] MERGE: ghi lặp an toàn một đơn và dòng hàng của đơn (chạy nhiều lần vẫn một node, một quan hệ)
MATCH (p:Product {id: 'p-egg'})
MERGE (o:Order {id: 'o-4'}) SET o.code = 'HD261107-0010', o.at = datetime()
MERGE (o)-[r:CONTAINS]->(p) SET r.qty = 2, r.gift = false
RETURN o, r, p;

// [CB-N3] Các đơn có một sản phẩm, mới nhất trước
MATCH (o:Order)-[r:CONTAINS]->(p:Product {sku: 'SUA-VNM-1L'})
RETURN o.code, o.at, r.qty ORDER BY o.at DESC;
