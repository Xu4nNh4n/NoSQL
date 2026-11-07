package com.api.supermarket.common;

import java.time.LocalDate;

import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Sinh mã chứng từ bằng bộ đếm nguyên tử trong collection {@code counters}
 * ({@code findAndModify} + {@code $inc}, upsert). Không dùng Redis vì Redis có thể mất dữ liệu.
 * Gọi được trong transaction đang mở: bộ đếm cũng được rollback cùng nghiệp vụ.
 */
@Component
public class CodeGenerator {
    static final String COLLECTION = "counters";

    private final MongoTemplate mongo;
    private final AppTime time;

    public CodeGenerator(MongoTemplate mongo, AppTime time) {
        this.mongo = mongo;
        this.time = time;
    }

    public String next(DocumentType type) {
        LocalDate day = time.today();
        Document counter = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(DocumentCodes.counterKey(type, day))),
                new Update().inc("seq", 1L),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                Document.class,
                COLLECTION);
        long seq = ((Number) counter.get("seq")).longValue();
        return DocumentCodes.format(type, day, seq);
    }
}
