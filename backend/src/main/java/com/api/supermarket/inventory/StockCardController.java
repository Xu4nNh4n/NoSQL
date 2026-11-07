package com.api.supermarket.inventory;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.catalog.product.ProductService;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.Months;

/** Thẻ kho của sản phẩm theo tháng (FR-06), đọc từ Cassandra. Mọi vai trò được xem. */
@RestController
public class StockCardController {
    private final StockLedger ledger;
    private final ProductService products;
    private final AppTime time;

    public StockCardController(StockLedger ledger, ProductService products, AppTime time) {
        this.ledger = ledger;
        this.products = products;
        this.time = time;
    }

    @GetMapping("/api/products/{id}/stock-movements")
    public List<StockMovement> stockCard(@PathVariable String id, @RequestParam(required = false) String month) {
        products.load(id);
        return ledger.read(id, Months.parseOrCurrent(month, time));
    }
}
