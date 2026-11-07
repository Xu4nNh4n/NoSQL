package com.api.supermarket.config;

import org.springframework.data.mongodb.core.MongoTemplate;

/** Mỗi module khai báo index (và schema validation) của collection mình quản lý. */
public interface MongoIndexContributor {
    void ensureIndexes(MongoTemplate mongo);
}
