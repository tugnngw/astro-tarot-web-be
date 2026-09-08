package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.shop.CategoryResponse;
import com.exe.astratarot.domain.dto.shop.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductService {
    Page<ProductResponse> list(String categorySlug, String keyword, Pageable pageable);
    ProductResponse getBySlug(String slug);
    List<ProductResponse> getFeatured();
    List<CategoryResponse> getCategories();
}
