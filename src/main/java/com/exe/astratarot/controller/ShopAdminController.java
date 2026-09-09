package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.shop.AffiliateStatsResponse;
import com.exe.astratarot.domain.dto.shop.ProductResponse;
import com.exe.astratarot.domain.dto.shop.SaveProductRequest;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.ProductAdminService;
import com.exe.astratarot.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Quản lý sản phẩm liên kết, và ghi nhận lượt bấm sang sàn.
 *
 * <p>Endpoint ghi nhận lượt bấm là CÔNG KHAI: phần lớn người bấm mua chưa đăng
 * nhập, và bắt họ đăng nhập chỉ để đi mua hộ mình là cách chắc chắn nhất để
 * mất hoa hồng.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ShopAdminController {

    private final ProductAdminService productAdminService;
    private final ProductService productService;

    // ---------- Lượt bấm (công khai) ----------

    /**
     * Ghi nhận lượt bấm rồi trả về đường dẫn sang sàn.
     *
     * <p>Trả link về cho giao diện tự mở, thay vì server trả 302: mở bằng
     * window.open ngay trong tay người dùng thì trình duyệt không chặn popup,
     * còn chuyển hướng vòng qua server sẽ mất referrer của sàn liên kết.
     */
    @PostMapping("/shop/products/{slug}/click")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<Map<String, String>>> click(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable String slug,
            HttpServletRequest request) {
        String url = productAdminService.registerClick(
                me == null ? null : me.getUser().getId(),
                slug,
                request.getHeader("Referer"));
        return ResponseEntity.ok(ApiResponse.success(Map.of("url", url)));
    }

    // ---------- Quản lý sản phẩm ----------

    /** Danh sách cho màn quản trị: gồm cả sản phẩm đã ẩn. */
    @GetMapping("/admin/products")
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                productService.listForAdmin(keyword, PageRequest.of(page, size))));
    }

    @PostMapping("/admin/products")
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @AuthenticationPrincipal CustomUserDetails me,
            @Valid @RequestBody SaveProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã tạo sản phẩm",
                productAdminService.create(me.getUser().getId(), request)));
    }

    @PutMapping("/admin/products/{id}")
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID id,
            @Valid @RequestBody SaveProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật sản phẩm",
                productAdminService.update(me.getUser().getId(), id, request)));
    }

    /** Ẩn hoặc hiện lại. Không có xoá cứng — sản phẩm đã có lượt bấm là số liệu. */
    @PatchMapping("/admin/products/{id}/active")
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public ResponseEntity<ApiResponse<ProductResponse>> setActive(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID id,
            @RequestParam boolean value) {
        return ResponseEntity.ok(ApiResponse.success(
                value ? "Đã hiện lại sản phẩm" : "Đã ẩn sản phẩm",
                productAdminService.setActive(me.getUser().getId(), id, value)));
    }

    @GetMapping("/admin/affiliate/stats")
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public ResponseEntity<ApiResponse<AffiliateStatsResponse>> stats(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ApiResponse.success(productAdminService.stats(days)));
    }
}
