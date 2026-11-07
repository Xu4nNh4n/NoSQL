package com.api.supermarket.common;

/** Loại chứng từ và tiền tố mã hiển thị theo SRS v2.0 (ví dụ HD261107-0042). */
public enum DocumentType {
    ORDER("HD"),
    PURCHASE_ORDER("PN"),
    BATCH("L"),
    RETURN("PT");

    private final String prefix;

    DocumentType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
