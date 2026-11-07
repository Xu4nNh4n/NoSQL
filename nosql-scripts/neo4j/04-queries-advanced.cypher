// 04-queries-advanced.cypher – truy vấn nhiều bước quan hệ, thế mạnh của đồ thị so với JOIN nhiều tầng.

// [NC-N1] AP-20 (FR-12): truy vết lô lỗi → các đơn đã lấy hàng từ lô – GraphQueryService.TRACE
MATCH (b:Batch {code: 'L261101-0003'})<-[t:TOOK_FROM]-(o:Order)
RETURN o.code AS don, o.at AS luc, t.qty AS so_luong
ORDER BY luc;

// [NC-N2] AP-21 (FR-13): gợi ý mua kèm "khách mua A thường mua B" (2 bước quan hệ, bỏ dòng quà) – GraphQueryService.ALSO_BOUGHT
MATCH (p:Product {id: 'p-milk'})<-[r1:CONTAINS]-(o:Order)-[r2:CONTAINS]->(x:Product)
WHERE x <> p AND NOT coalesce(r1.gift, false) AND NOT coalesce(r2.gift, false)
RETURN x.name AS goi_y, count(DISTINCT o) AS so_don
ORDER BY so_don DESC, goi_y LIMIT 5;

// [NC-N3] Thu hồi theo nhà cung cấp: mọi đơn đã lấy hàng từ BẤT KỲ lô nào của NCC, gom theo sản phẩm (3 bước quan hệ)
MATCH (s:Supplier {name: 'Vinamilk'})<-[:SUPPLIED_BY]-(b:Batch)<-[t:TOOK_FROM]-(o:Order),
      (b)-[:OF_PRODUCT]->(p:Product)
RETURN p.name AS san_pham, collect(DISTINCT b.code) AS cac_lo, count(DISTINCT o) AS so_don, sum(t.qty) AS tong_da_ban
ORDER BY tong_da_ban DESC;

// [NC-N4] Đường đi ngắn nhất từ nhà cung cấp tới một đơn (giải thích vì sao đơn nằm trong danh sách thu hồi)
MATCH (s:Supplier {id: 's-vnm'}), (o:Order {id: 'o-2'})
MATCH path = shortestPath((s)-[*..6]-(o))
RETURN [n IN nodes(path) | coalesce(n.code, n.name)] AS duong_di, length(path) AS so_buoc;

// [NC-N5] Đơn giống nhau (cùng có ≥ 2 sản phẩm chung) – nhận ra "giỏ hàng" hay gặp để gợi ý combo khuyến mãi
MATCH (o1:Order)-[:CONTAINS]->(p:Product)<-[:CONTAINS]-(o2:Order)
WHERE o1.id < o2.id
WITH o1, o2, collect(DISTINCT p.name) AS chung
WHERE size(chung) >= 2
RETURN o1.code, o2.code, chung ORDER BY size(chung) DESC LIMIT 20;

// [NC-N6] Sản phẩm bán kèm nhiều nhất toàn cửa hàng (cặp sản phẩm cùng đơn), dùng để chọn cặp tạo "mua X tặng Y"
MATCH (a:Product)<-[:CONTAINS]-(o:Order)-[:CONTAINS]->(b:Product)
WHERE a.id < b.id
RETURN a.name, b.name, count(o) AS so_don ORDER BY so_don DESC LIMIT 10;

// [NC-N7] Dựng lại: xóa theo lô 5.000 node để không vượt bộ nhớ transaction (GraphRebuildService.clear)
MATCH (n) WITH n LIMIT 5000 DETACH DELETE n RETURN count(*) AS da_xoa;
