package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.blog.BlogListResponse;
import com.exe.astratarot.domain.dto.blog.BlogResponse;
import com.exe.astratarot.domain.dto.blog.ReviewBlogRequest;
import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.enums.BlogStatus;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.security.SecurityPermissions;
import com.exe.astratarot.service.BlogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller cho Public và Manager/Admin: xem bài viết công khai và quản lý.
 *
 * <p>Public: chỉ xem bài PUBLISHED.
 * Manager/Admin: duyệt bài (APPROVED/REJECTED) và xuất bản (PUBLISHED).
 */
@RestController
@RequestMapping("/api/v1/blogs")
@RequiredArgsConstructor
@Slf4j
public class BlogController {

    private final BlogService blogService;

    /**
     * Danh sách bài viết công khai (PUBLISHED).
     * - Khách chưa đăng nhập cũng xem được.
     */
    @GetMapping
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<BlogListResponse>> listPublished(
            @RequestParam(required = false) BlogStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                blogService.listPublic(status, pageable)));
    }

    /**
     * Xem chi tiết bài viết công khai.
     * - Chỉ thấy bài có status = PUBLISHED.
     */
    @GetMapping("/{slug}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<BlogResponse>> getBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success("Xem chi tiết bài viết",
                blogService.getBySlug(slug)));
    }

    /**
     * Xem tất cả bài viết (admin/staff).
     * - Manager/Admin xem toàn bộ bài viết.
     */
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('" + SecurityPermissions.BLOG_REVIEW + "')")
    public ResponseEntity<ApiResponse<BlogListResponse>> listAll(
            @RequestParam(required = false) BlogStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                blogService.listAll(pageable)));
    }

    /**
     * Duyệt bài viết (APPROVED/REJECTED).
     * - Chỉ Manager/Admin được duyệt.
     */
    @PatchMapping("/{id}/review")
    @PreAuthorize("hasAuthority('" + SecurityPermissions.BLOG_REVIEW + "')")
    public ResponseEntity<ApiResponse<BlogResponse>> review(
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            @PathVariable UUID id,
            @RequestBody ReviewBlogRequest request) {
        UUID adminId = adminDetails.getUser().getId();
        return ResponseEntity.ok(ApiResponse.success("Đã duyệt bài viết",
                blogService.review(adminId, id, request)));
    }

    /**
     * Xuất bản bài viết (APPROVED -> PUBLISHED).
     * - Chỉ Manager/Admin được publish.
     */
    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('" + SecurityPermissions.BLOG_REVIEW + "')")
    public ResponseEntity<ApiResponse<BlogResponse>> publish(
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            @PathVariable UUID id) {
        UUID adminId = adminDetails.getUser().getId();
        ReviewBlogRequest request = ReviewBlogRequest.builder()
                .action("PUBLISH")
                .build();
        return ResponseEntity.ok(ApiResponse.success("Đã xuất bản bài viết",
                blogService.review(adminId, id, request)));
    }

    /**
     * Xóa bài viết.
     * - Chỉ Manager/Admin được xóa.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + SecurityPermissions.BLOG_REVIEW + "')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            @PathVariable UUID id) {
        UUID adminId = adminDetails.getUser().getId();
        blogService.delete(adminId, id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa bài viết", null));
    }
}
