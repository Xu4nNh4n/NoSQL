# Sao lưu và khôi phục Neo4j

Đồ thị là dữ liệu **suy ra** từ MongoDB (đơn, lô, khách, nhà cung cấp), nên cách khôi phục chính là dựng lại:

```http
POST /api/admin/graph/rebuild        (ADMIN)
```

Backend xóa graph theo lô 5.000 node, đọc `batches` và `orders` từ MongoDB theo trang 500 rồi `MERGE` lại. Kết quả trả số node đã xóa, số lô, số đơn và thời gian (NFR-10: < 5 phút với 10 nghìn đơn).

## Snapshot của Aura

AuraDB Free có snapshot hằng ngày do Neo4j quản lý (Console → instance → *Snapshots*); có thể tải về dạng `.backup` và khôi phục vào instance mới. Gói Free tự tạm dừng khi lâu không dùng: mở lại instance trước buổi demo.

## Xuất dữ liệu đồ thị (APOC có sẵn trên Aura)

```cypher
CALL apoc.export.json.all(null, {stream: true}) YIELD data RETURN data;
```
