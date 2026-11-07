package com.api.supermarket.common;

/** Một CSDL phụ (Cassandra, Neo4j) không khả dụng. Trả HTTP 503. */
public class DatastoreUnavailableException extends RuntimeException {
    private final String datastore;

    public DatastoreUnavailableException(String datastore, Throwable cause) {
        super(datastore + " tạm thời không khả dụng", cause);
        this.datastore = datastore;
    }

    public String datastore() {
        return datastore;
    }
}
