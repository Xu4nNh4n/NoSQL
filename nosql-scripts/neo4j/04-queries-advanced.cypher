// 04-queries-advanced.cypher – truy vấn nhiều bước quan hệ, thế mạnh của đồ thị so với JOIN nhiều tầng.

// [NC-N1] AP-20 (FR-13): truy vết lô lỗi → đơn đã lấy hàng từ lô → khách cần liên hệ – GraphQueryService.TRACE
MATCH (b:Batch {code: 'L261101-0003'})<-[t:TOOK_FROM]-(o:Order)
OPTIONAL MATCH (c:Customer)-[:PLACED]->(o)
RETURN o.code AS don, o.at AS luc, t.qty AS so_luong, c.name AS khach, c.phone AS sdt
ORDER BY luc;

// [NC-N2] AP-21 (FR-14): gợi ý mua kèm "khách mua A thường mua B" (2 bước quan hệ, bỏ dòng quà) – GraphQueryService.ALSO_BOUGHT
MATCH (p:Product {id: 'p-milk'})<-[r1:CONTAINS]-(o:Order)-[r2:CONTAINS]->(x:Product)
WHERE x <> p AND NOT coalesce(r1.gift, false) AND NOT coalesce(r2.gift, false)
RETURN x.name AS goi_y, count(DISTINCT o) AS so_don
ORDER BY so_don DESC, goi_y LIMIT 5;

// [NC-N3] Thu hồi theo nhà cung cấp: mọi khách đã mua hàng từ BẤT KỲ lô nào của NCC (3 bước quan hệ)
MATCH (s:Supplier {name: 'Vinamilk'})<-[:SUPPLIED_BY]-(b:Batch)<-[t:TOOK_FROM]-(o:Order)<-[:PLACED]-(c:Customer)
RETURN c.name, c.phone, collect(DISTINCT b.code) AS cac_lo, sum(t.qty) AS tong_da_mua
ORDER BY tong_da_mua DESC;

// [NC-N4] Đường đi ngắn nhất từ nhà cung cấp tới một khách (giải thích vì sao khách nằm trong danh sách thu hồi)
MATCH (s:Supplier {id: 's-vnm'}), (c:Customer {id: 'k-cuong'})
MATCH path = shortestPath((s)-[*..6]-(c))
RETURN [n IN nodes(path) | coalesce(n.code, n.name)] AS duong_di, length(path) AS so_buoc;

// [NC-N5] Khách hàng mua giống nhau (cùng mua ≥ 2 sản phẩm chung) – gợi ý nhóm khách cho chương trình khuyến mãi
MATCH (c1:Customer)-[:PLACED]->(:Order)-[:CONTAINS]->(p:Product)<-[:CONTAINS]-(:Order)<-[:PLACED]-(c2:Customer)
WHERE c1.id < c2.id
WITH c1, c2, collect(DISTINCT p.name) AS chung
WHERE size(chung) >= 2
RETURN c1.name, c2.name, chung ORDER BY size(chung) DESC;

// [NC-N6] Sản phẩm bán kèm nhiều nhất toàn cửa hàng (cặp sản phẩm cùng đơn), dùng để chọn cặp tạo "mua X tặng Y"
MATCH (a:Product)<-[:CONTAINS]-(o:Order)-[:CONTAINS]->(b:Product)
WHERE a.id < b.id
RETURN a.name, b.name, count(o) AS so_don ORDER BY so_don DESC LIMIT 10;

// [NC-N7] Dựng lại: xóa theo lô 5.000 node để không vượt bộ nhớ transaction (GraphRebuildService.clear)
MATCH (n) WITH n LIMIT 5000 DETACH DELETE n RETURN count(*) AS da_xoa;
