package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.blog.BlogListResponse;
import com.exe.astratarot.domain.dto.blog.BlogResponse;
import com.exe.astratarot.domain.dto.blog.CreateBlogRequest;
import com.exe.astratarot.domain.dto.blog.UpdateBlogRequest;
import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.BlogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller cho Staff/Author: tạo, sửa, gửi duyệt bài viết.
 *
 * <p>Các endpoint trong controller này yêu cầu role STAFF, MANAGER hoặc ADMIN.
 * Author có thể tạo/sửa bài của chính mình, nhưng không được duyệt bài.
 */
@RestController
@RequestMapping("/api/v1/staff/blogs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STAFF')")
public class StaffBlogController {

    private final BlogService blogService;

    /**
     * Tạo bài viết mới (DRAFT).
     * - Chỉ Staff/Reader được phép tạo.
     * - Bài mới luôn bắt đầu với status = DRAFT.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<BlogResponse>> create(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CreateBlogRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã tạo bài viết nháp",
                blogService.create(currentUser.getUser().getId(), request)));
    }

    /**
     * Cập nhật bài viết của chính mình.
     * - Author: update bài của chính mình (chỉ DRAFT/PENDING).
     * - Staff/Admin: update mọi bài (chỉ DRAFT/PENDING).
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BlogResponse>> update(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBlogRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật bài viết",
                blogService.update(currentUser.getUser().getId(), id, request)));
    }

    /**
     * Gửi bài viết chờ duyệt (DRAFT -> PENDING).
     * - Chỉ author được gửi lại.
     */
    @PatchMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<BlogResponse>> submitForReview(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Đã gửi bài viết chờ duyệt",
                blogService.submitForReview(currentUser.getUser().getId(), id)));
    }

    /**
     * Xem danh sách bài viết của chính mình.
     * - Author xem tất cả bài của mình (DRAFT, PENDING, APPROVED, REJECTED, PUBLISHED).
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<BlogListResponse>> myBlogs(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                blogService.listByAuthor(currentUser.getUser().getId(), pageable)));
    }

    /**
     * Xóa bài viết.
     * - Author: xóa bài của chính mình (chỉ DRAFT).
     * - Staff/Admin: xóa mọi bài.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable UUID id) {
        blogService.delete(currentUser.getUser().getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa bài viết", null));
    }
}
