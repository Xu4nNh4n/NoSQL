# Thiết kế key Redis

Redis chỉ giữ **dữ liệu tạm**: mất dữ liệu Redis không mất đơn hàng hay tồn kho (nguồn sự thật là MongoDB). Mọi lệnh trong backend đi qua `RedisSafe`: Redis lỗi thì ghi log và trả giá trị an toàn, việc bán vẫn chạy (NFR-09). Gói free: `maxmemory-policy allkeys-lru`.

| Key | Kiểu | TTL | Ghi khi | Đọc khi | Lớp backend |
|---|---|---|---|---|---|
| `auth:refresh:{sha256(token)}` | string → userId | 7 ngày | đăng nhập, làm mới | làm mới (`GETDEL`, dùng một lần) | `RefreshTokenStore` |
| `auth:user-tokens:{userId}` | set các sha256 | 7 ngày | đăng nhập | khóa tài khoản / đổi mật khẩu (thu hồi hết) | `RefreshTokenStore` |
| `cache:products:active` | string JSON | 5 phút | lần đọc đầu (cache-aside) | màn hình bán hàng | `ProductCache` |
| `cache:products:low-stock` | string JSON | 1 phút | lần đọc đầu | cảnh báo sắp hết hàng | `LowStockService` |
| `cache:promotions:active` | string JSON | 5 phút | lần đọc đầu | tính giá ở quầy | `PromotionCache` |
| `cache:batches:expiring-soon` | string JSON | 24 giờ | job 07:00 | thủ kho đầu ca | `ExpiringSoonService` |
| `pos:held:{cashierId}:{holdId}` | string JSON giỏ | 30 phút | treo đơn | mở lại (Lua GET+DEL+ZREM) | `HeldCartService` |
| `pos:held-index:{cashierId}` | sorted set holdId → epoch ms | 30 phút | treo đơn | liệt kê đơn treo | `HeldCartService` |
| `top:sales:{yyyyMMdd}` | sorted set productId → số lượng | 3 ngày | mỗi đơn (`ZINCRBY`, bỏ hàng tặng) | top 10 hôm nay (`ZREVRANGE … WITHSCORES`) | `TopSalesService` |

Xóa cache khi dữ liệu gốc đổi: ghi sản phẩm, đổi tên nhóm hàng, nhập lô, bán, trả → `DEL cache:products:active cache:products:low-stock`; sửa khuyến mãi → `DEL cache:promotions:active`.

Không dùng Redis cho: bộ đếm mã chứng từ (MongoDB `counters`), chống gửi lặp (index unique `requestKey` trên MongoDB), khóa bán hàng (transaction + cập nhật có điều kiện của MongoDB) – vì các việc này không được phép mất khi Redis mất dữ liệu.
