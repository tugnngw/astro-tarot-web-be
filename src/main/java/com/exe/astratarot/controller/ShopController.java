package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.shop.CategoryResponse;
import com.exe.astratarot.domain.dto.shop.ProductResponse;
import com.exe.astratarot.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catalog công khai: duyệt sản phẩm không cần đăng nhập.
 * Các endpoint này được khai báo permitAll trong SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/shop")
@RequiredArgsConstructor
public class ShopController {

    private static final int MAX_PAGE_SIZE = 60;

    private final ProductService productService;

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> categories() {
        return ResponseEntity.ok(ApiResponse.success(productService.getCategories()));
    }

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> products(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        // Chặn size quá lớn để một request không kéo cả bảng về.
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort sort = Sort.by(
                "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC,
                sortBy);

        Page<ProductResponse> result =
                productService.list(category, keyword, PageRequest.of(Math.max(page, 0), safeSize, sort));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/products/featured")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> featured() {
        return ResponseEntity.ok(ApiResponse.success(productService.getFeatured()));
    }

    @GetMapping("/products/{slug}")
    public ResponseEntity<ApiResponse<ProductResponse>> detail(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(productService.getBySlug(slug)));
    }
}
