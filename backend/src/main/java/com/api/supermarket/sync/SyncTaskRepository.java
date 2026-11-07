package com.api.supermarket.sync;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import com.api.supermarket.config.MongoIndexContributor;

/** Truy cập collection {@code sync_queue}. Mọi lệnh đổi trạng thái chỉ áp lên bản ghi còn PENDING. */
@Repository
public class SyncTaskRepository implements MongoIndexContributor {
    private final MongoTemplate mongo;

    public SyncTaskRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void ensureIndexes(MongoTemplate template) {
        template.indexOps(SyncTask.COLLECTION).createIndex(new Index()
                .on("status", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.ASC)
                .named("status_createdAt"));
    }

    public SyncTask insert(SyncTask task) {
        return mongo.insert(task);
    }

    public Optional<SyncTask> findById(String id) {
        return Optional.ofNullable(mongo.findById(id, SyncTask.class));
    }

    public void markDone(String id, Instant at) {
        mongo.updateFirst(pending(id), new Update().set("status", SyncStatus.DONE).set("doneAt", at)
                .set("lastError", null), SyncTask.class);
    }

    /** Tăng số lần thử; {@code giveUp} thì chuyển FAILED để chờ ADMIN bấm thử lại. */
    public void recordFailure(String id, String error, boolean giveUp) {
        Update update = new Update().inc("attempts", 1).set("lastError", error);
        if (giveUp) {
            update.set("status", SyncStatus.FAILED);
        }
        mongo.updateFirst(pending(id), update, SyncTask.class);
    }

    public List<SyncTask> findPendingOlderThan(Instant olderThan, int limit) {
        Query query = Query.query(Criteria.where("status").is(SyncStatus.PENDING).and("createdAt").lt(olderThan))
                .with(Sort.by("createdAt")).limit(limit);
        return mongo.find(query, SyncTask.class);
    }

    public List<SyncTask> findPendingByOwner(String ownerId) {
        Query query = Query.query(Criteria.where("status").is(SyncStatus.PENDING).and("ownerId").is(ownerId))
                .with(Sort.by("createdAt"));
        return mongo.find(query, SyncTask.class);
    }

    public long resetFailed() {
        return mongo.updateMulti(Query.query(Criteria.where("status").is(SyncStatus.FAILED)),
                new Update().set("status", SyncStatus.PENDING).set("attempts", 0), SyncTask.class)
                .getModifiedCount();
    }

    public long countByStatus(SyncStatus status) {
        return mongo.count(Query.query(Criteria.where("status").is(status)), SyncTask.class);
    }

    public Optional<Instant> oldestPendingAt() {
        Query query = Query.query(Criteria.where("status").is(SyncStatus.PENDING)).with(Sort.by("createdAt")).limit(1);
        return Optional.ofNullable(mongo.findOne(query, SyncTask.class)).map(SyncTask::getCreatedAt);
    }

    public List<SyncTask> recentFailures(int limit) {
        Query query = Query.query(Criteria.where("status").is(SyncStatus.FAILED))
                .with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(limit);
        return mongo.find(query, SyncTask.class);
    }

    private static Query pending(String id) {
        return Query.query(Criteria.where("_id").is(id).and("status").is(SyncStatus.PENDING));
    }
}
