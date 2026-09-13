package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.blog.BlogListResponse;
import com.exe.astratarot.domain.dto.blog.BlogResponse;
import com.exe.astratarot.domain.dto.blog.CreateBlogRequest;
import com.exe.astratarot.domain.dto.blog.ReviewBlogRequest;
import com.exe.astratarot.domain.dto.blog.UpdateBlogRequest;
import com.exe.astratarot.domain.enums.BlogStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BlogService {

    /**
     * Tạo bài viết mới.
     * - Chỉ Staff/Reader được phép tạo.
     * - Bài mới luôn bắt đầu với status = DRAFT.
     */
    BlogResponse create(UUID userId, CreateBlogRequest request);

    /**
     * Lấy chi tiết bài viết.
     * - Công khai: chỉ thấy bài có status = PUBLISHED.
     * - Internal: xem toàn bộ thông tin (reviewer, rejectionReason,...) nếu là
     *   author, reviewer, hoặc có quyền admin/staff.
     */
    BlogResponse get(UUID actorId, UUID blogId);

    /**
     * Cập nhật bài viết.
     * - Author: update bài của chính mình.
     * - Staff/Admin: update mọi bài.
     */
    BlogResponse update(UUID actorId, UUID blogId, UpdateBlogRequest request);

    /**
     * Xóa bài viết.
     * - Author: xóa bài của chính mình (chỉ khi chưa được duyệt).
     * - Staff/Admin: xóa mọi bài.
     */
    void delete(UUID actorId, UUID blogId);

    /**
     * Gửi bài duyệt (DRAFT -> PENDING).
     * - Chỉ author được gửi lại.
     */
    BlogResponse submitForReview(UUID actorId, UUID blogId);

    /**
     * Duyệt bài (PENDING -> APPROVED/REJECTED/PUBLISHED).
     * - Chỉ Staff/Manager/Admin được duyệt.
     */
    BlogResponse review(UUID actorId, UUID blogId, ReviewBlogRequest request);

    /**
     * Danh sách bài viết theo status (công khai).
     * - Chỉ thấy bài có status = PUBLISHED.
     */
    BlogListResponse listPublic(BlogStatus status, Pageable pageable);

    /**
     * Danh sách bài viết của author (internal).
     */
    BlogListResponse listByAuthor(UUID authorId, Pageable pageable);

    /**
     * Danh sách tất cả bài viết (admin/staff).
     */
    BlogListResponse listAll(Pageable pageable);

    /**
     * Lấy bài viết theo slug (công khai).
     * - Chỉ thấy bài có status = PUBLISHED.
     */
    BlogResponse getBySlug(String slug);

    /**
     * Lấy bài viết theo slug (internal - cho phép author/xem chi tiết).
     */
    BlogResponse getBySlugWithAuth(String slug, UUID actorId);
}
