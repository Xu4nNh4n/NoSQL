package com.api.supermarket.health;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.api.supermarket.health.DatastoreHealthService.DatastoreStatus;
import com.api.supermarket.support.CloudTest;

/** Cần .env có đủ 4 CSDL cloud. Context khởi động với app.schema.init=true nên cũng tạo bảng/constraint. */
@CloudTest
class DatastoreHealthCloudTest {
    @Autowired
    DatastoreHealthService health;

    @Test
    void allFourStoresAreUp() {
        List<DatastoreStatus> result = health.check();
        result.forEach(s -> System.out.println(s));
        assertTrue(result.stream().allMatch(DatastoreStatus::up), result.toString());
    }
}
