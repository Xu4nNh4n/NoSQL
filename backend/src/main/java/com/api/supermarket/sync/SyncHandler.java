package com.api.supermarket.sync;

import java.util.Set;

/**
 * Ghi một hoặc nhiều loại sự kiện sang một CSDL phụ. Một loại sự kiện có thể có nhiều handler
 * (ví dụ ORDER_CREATED ghi cả Cassandra lẫn Neo4j). Handler phải ghi lặp an toàn:
 * dùng khóa xác định (UUID tạo từ nội dung, MERGE theo id) vì một việc có thể chạy lại.
 */
public interface SyncHandler {
    Set<String> types();

    void apply(SyncTask task);
}
