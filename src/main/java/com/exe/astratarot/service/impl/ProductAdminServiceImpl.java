package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.shop.AffiliateStatsResponse;
import com.exe.astratarot.domain.dto.shop.ProductResponse;
import com.exe.astratarot.domain.dto.shop.SaveProductRequest;
import com.exe.astratarot.domain.entity.Product;
import com.exe.astratarot.domain.entity.ProductCategory;
import com.exe.astratarot.domain.entity.ProductClick;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.ProductCategoryRepository;
import com.exe.astratarot.repository.ProductClickRepository;
import com.exe.astratarot.repository.ProductRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.security.AdminActions;
import com.exe.astratarot.service.ActivityLogService;
import com.exe.astratarot.service.ProductAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Quản lý sản phẩm liên kết.
 *
 * <p>Shop không còn bán hàng trực tiếp: hàng nằm trên sàn, mình giới thiệu và
 * ăn hoa hồng. Nên việc của màn quản trị là gắn đúng link và theo dõi sản phẩm
 * nào được bấm nhiều, chứ không phải quản lý tồn kho.
 *
 * <p>Số hoa hồng ở đây chỉ là ƯỚC LƯỢNG dựa trên lượt bấm và tỉ lệ khai báo.
 * Doanh thu thật nằm ở báo cáo của sàn — mình không biết được ai bấm rồi có
 * mua hay không.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAdminServiceImpl implements ProductAdminService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductClickRepository clickRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    /** Sàn được phép gắn. Khớp với CHECK constraint ở V2_2. */
    private static final Set<String> PLATFORMS = Set.of("SHOPEE", "LAZADA", "TIKI", "TIKTOK", "OTHER");

    // =========================================================
    // Tạo và sửa
    // =========================================================

    @Override
    @Transactional
    public ProductResponse create(UUID actorId, SaveProductRequest request) {
        Product product = new Product();
        apply(product, request);
        product.setSlug(uniqueSlug(request.getName(), null));
        productRepository.save(product);

        activityLogService.record(actorId, AdminActions.PRODUCT_CREATE, AdminActions.ENTITY_PRODUCT,
                product.getId(), Map.of("name", product.getName()));
        return toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse update(UUID actorId, UUID productId, SaveProductRequest request) {
        Product product = find(productId);
        Map<String, Object> changes = new LinkedHashMap<>();

        if (!product.getName().equals(request.getName().trim())) {
            changes.put("name", Map.of("from", product.getName(), "to", request.getName().trim()));
        }
        if (!java.util.Objects.equals(product.getAffiliateUrl(), request.getAffiliateUrl())) {
            changes.put("affiliateUrl", Map.of(
                    "from", String.valueOf(product.getAffiliateUrl()),
                    "to", String.valueOf(request.getAffiliateUrl())));
        }
        apply(product, request);
        productRepository.save(product);

        activityLogService.record(actorId, AdminActions.PRODUCT_UPDATE, AdminActions.ENTITY_PRODUCT,
                productId, changes);
        return toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse setActive(UUID actorId, UUID productId, boolean active) {
        Product product = find(productId);
        product.setActive(active);
        productRepository.save(product);

        // Ẩn chứ không xoá: sản phẩm đã có lượt bấm thì xoá là mất luôn số liệu
        // đo được, và những lượt bấm đó vẫn có thể đang sinh hoa hồng ở sàn.
        activityLogService.record(actorId, AdminActions.PRODUCT_SET_ACTIVE, AdminActions.ENTITY_PRODUCT,
                productId, Map.of("active", active));
        return toResponse(product);
    }

    // =========================================================
    // Lượt bấm sang sàn
    // =========================================================

    @Override
    @Transactional
    public String registerClick(UUID userIdOrNull, String slug, String referrer) {
        Product product = productRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + slug));

        if (product.getAffiliateUrl() == null || product.getAffiliateUrl().isBlank()) {
            throw new IllegalArgumentException("Sản phẩm này chưa gắn liên kết mua hàng");
        }

        User user = userIdOrNull == null ? null : userRepository.findById(userIdOrNull).orElse(null);
        clickRepository.save(ProductClick.builder()
                .product(product)
                .user(user)
                .referrer(referrer == null ? null : referrer.substring(0, Math.min(referrer.length(), 255)))
                .build());

        // Cộng dồn ngay trên sản phẩm để xếp hạng không phải gom cả bảng clicks.
        product.setClickCount(product.getClickCount() + 1);
        productRepository.save(product);

        return product.getAffiliateUrl();
    }

    @Override
    @Transactional(readOnly = true)
    public AffiliateStatsResponse stats(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        List<Product> top = productRepository.findTopClicked();
        long clicksInPeriod = clickRepository.countByCreatedAtAfter(since);
        long totalClicks = clickRepository.count();

        // Hoa hồng ước lượng: giá × tỉ lệ × lượt bấm. Cố ý KHÔNG gọi đây là
        // doanh thu — mình không biết ai bấm rồi có mua hay không. Con số này
        // chỉ để so sánh sản phẩm với nhau.
        long estimated = top.stream()
                .mapToLong(p -> p.getPrice() == null || p.getCommissionPercent() == null
                        ? 0
                        : BigDecimal.valueOf(p.getPrice())
                                .multiply(p.getCommissionPercent())
                                .divide(BigDecimal.valueOf(100))
                                .longValue() * p.getClickCount())
                .sum();

        return AffiliateStatsResponse.builder()
                .totalClicks(totalClicks)
                .clicksInPeriod(clicksInPeriod)
                .periodDays(days)
                .productsWithLink(productRepository.countByActiveTrueAndAffiliateUrlIsNotNull())
                .productsWithoutLink(productRepository.countByActiveTrueAndAffiliateUrlIsNull())
                .estimatedCommission(estimated)
                .topProducts(top.stream().limit(10).map(this::toResponse).toList())
                .build();
    }

    // =========================================================
    // Tiện ích
    // =========================================================

    private void apply(Product product, SaveProductRequest r) {
        ProductCategory category = r.getCategoryId() == null ? null
                : categoryRepository.findById(r.getCategoryId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục"));

        String platform = r.getAffiliatePlatform() == null || r.getAffiliatePlatform().isBlank()
                ? "SHOPEE"
                : r.getAffiliatePlatform().trim().toUpperCase(Locale.ROOT);
        if (!PLATFORMS.contains(platform)) {
            throw new IllegalArgumentException("Sàn không hợp lệ: " + platform);
        }

        String url = r.getAffiliateUrl() == null || r.getAffiliateUrl().isBlank()
                ? null
                : r.getAffiliateUrl().trim();
        // Chặn ngay ở đây thay vì để khách bấm rồi mới biết: link không phải
        // http(s) sẽ mở ra trang trắng hoặc bị trình duyệt chặn.
        if (url != null && !url.startsWith("https://") && !url.startsWith("http://")) {
            throw new IllegalArgumentException("Liên kết phải bắt đầu bằng http:// hoặc https://");
        }

        product.setName(r.getName().trim());
        product.setDescription(r.getDescription());
        product.setPrice(r.getPrice());
        product.setCompareAtPrice(r.getCompareAtPrice());
        product.setImageUrl(r.getImageUrl());
        product.setImageIsIllustrative(Boolean.TRUE.equals(r.getImageIsIllustrative()));
        product.setAffiliateUrl(url);
        product.setAffiliatePlatform(platform);
        product.setCommissionPercent(r.getCommissionPercent() == null
                ? BigDecimal.ZERO
                : r.getCommissionPercent());
        product.setFeatured(Boolean.TRUE.equals(r.getFeatured()));
        product.setCategory(category);
        if (r.getActive() != null) {
            product.setActive(r.getActive());
        }
    }

    /**
     * Slug từ tên, bỏ dấu tiếng Việt.
     *
     * Slug nằm trong URL nên phải ổn định và không dấu; đổi tên sản phẩm KHÔNG
     * đổi slug (xem update: chỉ sinh slug lúc tạo), vì đổi slug là làm hỏng mọi
     * đường dẫn đã chia sẻ.
     */
    private String uniqueSlug(String name, UUID excludeId) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isEmpty()) {
            base = "san-pham";
        }
        String candidate = base;
        int suffix = 2;
        while (productRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private Product find(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));
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
}
