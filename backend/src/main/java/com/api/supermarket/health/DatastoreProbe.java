package com.api.supermarket.health;

/** Kiểm tra một CSDL còn trả lời không. Ném lỗi nghĩa là CSDL đang DOWN. */
public interface DatastoreProbe {
    /** Tên hiển thị: MongoDB, Cassandra, Neo4j, Redis. */
    String name();

    /** Vai trò của CSDL trong hệ thống, hiện trên trang trạng thái. */
    String role();

    void ping() throws Exception;
}
