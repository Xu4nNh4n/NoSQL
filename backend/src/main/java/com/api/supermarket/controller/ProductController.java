package com.api.supermarket.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.dto.request.ProductRequest;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.dto.response.ProductResponse;
import com.api.supermarket.service.ProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Product Management - Quản lý sản phẩm", description = "APIs for quản lý sản phẩm, nhóm hàng trên sản phẩm, tra cứu SKU và điều chỉnh tồn kho")
public class ProductController {
    private final ProductService productService;
    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "Get all products - Lấy danh sách sản phẩm", description = "Filter and paginate products with keyword, category, supplier, and active status - Phân trang và tìm kiếm sản phẩm theo danh mục, nhà cung cấp, trạng thái")
    public PageResponse<ProductResponse> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "productId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryName,
            @RequestParam(required = false) Boolean active) {
        return productService.filterProducts(page, size, sortBy, sortDir, keyword, categoryName, active);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID - Lấy thông tin sản phẩm theo ID", description = "Retrieve detailed product information by ID - Lấy thông tin chi tiết một sản phẩm theo mã ID")
    public ProductResponse getProductById(@PathVariable Long id) {
        log.debug("Get product request, productId = {}", id);
        return productService.getProductById(id);
    }

    @GetMapping("/active")
    @Operation(summary = "Get active products - Lấy danh sách sản phẩm đang mở bán", description = "Retrieve all products currently active for sale - Lấy danh sách tất cả sản phẩm đang có hiệu lực bán hàng")
    public List<ProductResponse> getActiveProducts() {
        return productService.getActiveProducts();
    }

    @GetMapping("/sku/{skuCode}")
    @Operation(summary = "Get product by SKU - Lấy sản phẩm theo mã SKU", description = "Find product by unique SKU barcode - Tìm sản phẩm theo mã vạch SKU duy nhất")
    public ProductResponse getProductBySkuCode(@PathVariable String skuCode) {
        return productService.getProductBySkuCode(skuCode);
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Get low stock products - Lấy danh sách sản phẩm sắp hết hàng", description = "Get products where stock quantity is below or equal to reorder point - Lấy danh sách sản phẩm có tồn kho thấp dưới ngưỡng đặt hàng")
    public List<ProductResponse> getLowStockProducts() {
        return productService.getLowStockProducts();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @PostMapping
    @Operation(summary = "Create product - Tạo sản phẩm mới", description = "Add a new product to the catalog with opening stock movement - Thêm sản phẩm mới và tự động tạo giao dịch tồn kho khởi tạo")
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest request) {
        log.debug("Create product request, productName = {}, productSku = {}", request.getProductName(), request.getSkuCode());
        return productService.createProduct(request);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @PutMapping("/{id}")
    @Operation(summary = "Update product - Cập nhật sản phẩm", description = "Update product details; receive or adjust stock through inventory batches - Cập nhật thông tin sản phẩm; nhập và điều chỉnh tồn kho qua lô hàng")
    public ProductResponse updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        log.debug("Update product request, productId = {}, productName = {}", id, request.getProductName());
        return productService.updateProduct(id, request);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete product - Xóa (ngừng kinh doanh) sản phẩm", description = "Soft delete a product by deactivating it - Xóa mềm sản phẩm bằng cách chuyển isActive sang false")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        log.debug("Delete product request, productId = {}", id);
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
