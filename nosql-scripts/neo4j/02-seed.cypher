// 02-seed.cypher – đồ thị mẫu nhỏ để chạy 03/04. Đồ thị thật do backend ghi qua sync_queue
// hoặc dựng lại toàn bộ từ MongoDB bằng POST /api/admin/graph/rebuild.

MERGE (s:Supplier {id: 's-vnm'}) SET s.name = 'Vinamilk'
MERGE (milk:Product {id: 'p-milk'}) SET milk.sku = 'SUA-VNM-1L', milk.name = 'Sữa tươi Vinamilk 1L'
MERGE (bread:Product {id: 'p-bread'}) SET bread.sku = 'BANH-MI', bread.name = 'Bánh mì sandwich'
MERGE (egg:Product {id: 'p-egg'}) SET egg.sku = 'TRUNG-10', egg.name = 'Trứng gà hộp 10'
MERGE (b1:Batch {id: 'b-1'}) SET b1.code = 'L261101-0003', b1.expiry = '2026-11-20'
MERGE (b2:Batch {id: 'b-2'}) SET b2.code = 'L261105-0001', b2.expiry = '2026-11-28'
MERGE (b1)-[:OF_PRODUCT]->(milk) MERGE (b2)-[:OF_PRODUCT]->(milk)
MERGE (b1)-[:SUPPLIED_BY]->(s) MERGE (b2)-[:SUPPLIED_BY]->(s)
MERGE (c1:Customer {id: 'k-binh'}) SET c1.name = 'Nguyễn Thị Bình', c1.phone = '0912000001'
MERGE (c2:Customer {id: 'k-cuong'}) SET c2.name = 'Trần Văn Cường', c2.phone = '0912000002'
MERGE (o1:Order {id: 'o-1'}) SET o1.code = 'HD261102-0001', o1.at = datetime('2026-11-02T09:00:00+07:00')
MERGE (o2:Order {id: 'o-2'}) SET o2.code = 'HD261103-0004', o2.at = datetime('2026-11-03T18:20:00+07:00')
MERGE (o3:Order {id: 'o-3'}) SET o3.code = 'HD261106-0002', o3.at = datetime('2026-11-06T07:45:00+07:00')
MERGE (c1)-[:PLACED]->(o1) MERGE (c2)-[:PLACED]->(o2)
MERGE (o1)-[r1:CONTAINS]->(milk) SET r1.qty = 2, r1.gift = false
MERGE (o1)-[r2:CONTAINS]->(bread) SET r2.qty = 1, r2.gift = false
MERGE (o2)-[r3:CONTAINS]->(milk) SET r3.qty = 1, r3.gift = false
MERGE (o2)-[r4:CONTAINS]->(bread) SET r4.qty = 2, r4.gift = false
MERGE (o2)-[r5:CONTAINS]->(egg) SET r5.qty = 1, r5.gift = false
MERGE (o3)-[r6:CONTAINS]->(milk) SET r6.qty = 3, r6.gift = false
MERGE (o1)-[t1:TOOK_FROM]->(b1) SET t1.qty = 2
MERGE (o2)-[t2:TOOK_FROM]->(b1) SET t2.qty = 1
MERGE (o3)-[t3:TOOK_FROM]->(b2) SET t3.qty = 3;
