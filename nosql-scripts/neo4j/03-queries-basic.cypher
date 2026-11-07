// 03-queries-basic.cypher – truy vấn cơ bản. Backend luôn truyền giá trị bằng tham số ($code, $id), không ghép chuỗi.
// Trong Neo4j Browser đặt tham số trước: :param code => 'L261101-0003'

// [CB-N1] Tìm sản phẩm theo SKU và các lô của nó
MATCH (p:Product {sku: 'SUA-VNM-1L'})<-[:OF_PRODUCT]-(b:Batch)
RETURN p.name, b.code, b.expiry ORDER BY b.expiry;

// [CB-N2] MERGE: ghi lặp an toàn một khách hàng và đơn của khách (chạy nhiều lần vẫn một node, một quan hệ)
MERGE (c:Customer {id: 'k-dung'}) SET c.name = 'Lê Thị Dung', c.phone = '0912000003'
MERGE (o:Order {id: 'o-4'}) SET o.code = 'HD261107-0010', o.at = datetime()
MERGE (c)-[:PLACED]->(o)
RETURN c, o;

// [CB-N3] Các đơn của một khách, mới nhất trước
MATCH (c:Customer {phone: '0912000001'})-[:PLACED]->(o:Order)
RETURN o.code, o.at ORDER BY o.at DESC;
