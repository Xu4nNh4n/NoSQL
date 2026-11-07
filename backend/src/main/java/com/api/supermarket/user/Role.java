package com.api.supermarket.user;

/** Ba vai trò của hệ thống (SRS v2.0 mục 2.4). Vai trò là enum, không cần collection riêng. */
public enum Role {
    ADMIN("Chủ cửa hàng kiêm quản lý, có toàn quyền"),
    WAREHOUSE("Thủ kho: phiếu nhập, lô hàng, điều chỉnh tồn, thẻ kho"),
    STAFF("Thu ngân: bán tại quầy, đơn treo, trả hàng, chốt ca, khách hàng");

    private final String description;

    Role(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
