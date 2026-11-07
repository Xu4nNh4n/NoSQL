package com.api.supermarket.sync;

import java.time.Instant;

import org.bson.Document;
import org.springframework.data.annotation.Id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một việc cần ghi sang Cassandra/Neo4j, được ghi trong cùng transaction MongoDB với nghiệp vụ.
 * {@code payload} chứa sẵn dữ liệu cần ghi, để handler không phải tính lại.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@org.springframework.data.mongodb.core.mapping.Document(SyncTask.COLLECTION)
public class SyncTask {
    public static final String COLLECTION = "sync_queue";

    @Id
    private String id;
    /** Ví dụ ORDER_CREATED, BATCH_RECEIVED, RETURN_CREATED, MASTER_UPSERTED. */
    private String type;
    private String refId;
    private Document payload;
    private SyncStatus status;
    private int attempts;
    private String lastError;
    private Instant createdAt;
    private Instant doneAt;
    /** Thu ngân tạo bản ghi; chốt ca xử lý các bản ghi còn chờ của mình trước khi tính tổng. */
    private String ownerId;
}
