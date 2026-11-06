package com.api.supermarket.service;
import com.api.supermarket.dto.request.ProductRequest;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.dto.response.ProductResponse;
import java.util.*;

public interface ProductService {
    PageResponse<com.api.supermarket.dto.response.StockMovementResponse> getStockMovements(Long productId, int page, int size);
    //Lấy 1 sản phẩm theo id
    ProductResponse getProductById(Long id);

    //Thêm sản phẩm mới
    ProductResponse createProduct(ProductRequest request);

    //Cập nhật sản phẩm
    ProductResponse updateProduct(Long id, ProductRequest request);

    //Xóa sản phẩm
    void deleteProduct(Long id);

    //Lọc sản phẩm đang hoạt động
    List<ProductResponse> getActiveProducts();

    //Tìm sản phẩm theo mã sku
    ProductResponse getProductBySkuCode(String skuCode);

    //Lấy sản phẩm sắp hết hạn (stockQuantity <= reorderPoint)
    List<ProductResponse> getLowStockProducts();

    //Lấy danh sách sản phẩm với phân trang``   

    PageResponse<ProductResponse> filterProducts(
        int page,
        int size,
        String sortBy,
        String sortDir,
        String keyword,
        String categoryName,
        Boolean active
    );
}
