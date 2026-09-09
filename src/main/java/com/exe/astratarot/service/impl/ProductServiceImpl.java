package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.shop.CategoryResponse;
import com.exe.astratarot.domain.dto.shop.ProductResponse;
import com.exe.astratarot.domain.entity.Product;
import com.exe.astratarot.domain.entity.ProductCategory;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.ProductCategoryRepository;
import com.exe.astratarot.repository.ProductRepository;
import com.exe.astratarot.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> list(String categorySlug, String keyword, Pageable pageable) {
        // Chuẩn hoá về chuỗi rỗng khi không lọc: repository dùng "" làm sentinel
        // (xem chú thích ở ProductRepository.search). Nhờ vậy FE có thể gửi
        // ?keyword= mà không phải bỏ hẳn tham số ra khỏi URL.
        String category = isBlank(categorySlug) ? "" : categorySlug.trim();
        String kw = isBlank(keyword) ? "" : keyword.trim();
        return productRepository.search(category, kw, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getBySlug(String slug) {
        Product product = productRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + slug));
        return toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getFeatured() {
        return productRepository.findByActiveTrueAndFeaturedTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> listForAdmin(String keyword, Pageable pageable) {
        return productRepository
                .searchForAdmin(isBlank(keyword) ? "" : keyword.trim(), pageable)
                .map(this::toResponse);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private ProductResponse toResponse(Product p) {
        ProductCategory c = p.getCategory();
        return ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .slug(p.getSlug())
                .description(p.getDescription())
                .price(p.getPrice())
                .compareAtPrice(p.getCompareAtPrice())
                .stock(p.getStock())
                .imageUrl(p.getImageUrl())
                .imageIsIllustrative(p.getImageIsIllustrative())
                .affiliateUrl(p.getAffiliateUrl())
                .affiliatePlatform(p.getAffiliatePlatform())
                .commissionPercent(p.getCommissionPercent())
                .clickCount(p.getClickCount())
                .featured(p.getFeatured())
                .categoryName(c == null ? null : c.getName())
                .categorySlug(c == null ? null : c.getSlug())
                .build();
    }

    private CategoryResponse toResponse(ProductCategory c) {
        return CategoryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .slug(c.getSlug())
                .description(c.getDescription())
                .displayOrder(c.getDisplayOrder())
                .build();
    }
}
