package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.blog.*;
import com.exe.astratarot.domain.entity.Blog;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.BlogStatus;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.BlogRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.BlogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlogServiceImpl implements BlogService {

    private final BlogRepository blogRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public BlogResponse create(UUID userId, CreateBlogRequest request) {
        User author = findUser(userId);

        // Check if slug already exists
        if (blogRepository.existsBySlug(request.getSlug())) {
            throw new IllegalArgumentException("Slug đã tồn tại: " + request.getSlug());
        }

        Blog blog = Blog.builder()
                .title(request.getTitle())
                .slug(request.getSlug())
                .summary(request.getSummary())
                .content(request.getContent())
                .thumbnailUrl(request.getThumbnailUrl())
                .author(author)
                .status(BlogStatus.DRAFT)
                .build();

        blog = blogRepository.save(blog);

        log.info("Blog created: id={}, authorId={}", blog.getId(), userId);
        return toResponse(blog);
    }

    @Override
    @Transactional(readOnly = true)
    public BlogResponse get(UUID actorId, UUID blogId) {
        Blog blog = findBlog(blogId);

        // Public access: only published articles
        if (blog.getStatus() == BlogStatus.PUBLISHED) {
            return toResponse(blog);
        }

        // Internal access: author, reviewer, or staff/admin
        boolean isAuthor = blog.getAuthor().getId().equals(actorId);
        boolean isStaff = isStaffOrAdmin(actorId);

        if (!isAuthor && !isStaff) {
            throw new AccessDeniedException("Bạn không có quyền xem bài viết này");
        }

        return toResponse(blog);
    }

    @Override
    @Transactional
    public BlogResponse update(UUID actorId, UUID blogId, UpdateBlogRequest request) {
        Blog blog = findBlog(blogId);

        // Ownership check: author can only update own posts
        boolean isAuthor = blog.getAuthor().getId().equals(actorId);
        boolean isStaff = isStaffOrAdmin(actorId);

        if (!isAuthor && !isStaff) {
            throw new AccessDeniedException("Bạn không có quyền cập nhật bài viết này");
        }

        // Staff/Admin can only update DRAFT or PENDING articles
        if (isStaff && !Set.of(BlogStatus.DRAFT, BlogStatus.PENDING).contains(blog.getStatus())) {
            throw new IllegalArgumentException("Chỉ cập nhật được bài đang ở trạng thái DRAFT hoặc PENDING");
        }

        // Update fields
        if (request.getTitle() != null) {
            blog.setTitle(request.getTitle());
        }
        if (request.getSlug() != null && !request.getSlug().equals(blog.getSlug())) {
            if (blogRepository.existsBySlug(request.getSlug())) {
                throw new IllegalArgumentException("Slug đã tồn tại: " + request.getSlug());
            }
            blog.setSlug(request.getSlug());
        }
        if (request.getSummary() != null) {
            blog.setSummary(request.getSummary());
        }
        if (request.getContent() != null) {
            blog.setContent(request.getContent());
        }
        if (request.getThumbnailUrl() != null) {
            blog.setThumbnailUrl(request.getThumbnailUrl());
        }

        blog = blogRepository.save(blog);

        log.info("Blog updated: id={}, actorId={}", blog.getId(), actorId);
        return toResponse(blog);
    }

    @Override
    @Transactional
    public void delete(UUID actorId, UUID blogId) {
        Blog blog = findBlog(blogId);

        boolean isAuthor = blog.getAuthor().getId().equals(actorId);
        boolean isStaff = isStaffOrAdmin(actorId);

        if (!isAuthor && !isStaff) {
            throw new AccessDeniedException("Bạn không có quyền xóa bài viết này");
        }

        // Author can only delete DRAFT articles
        if (isAuthor && blog.getStatus() != BlogStatus.DRAFT) {
            throw new IllegalArgumentException("Chỉ xóa được bài đang ở trạng thái DRAFT");
        }

        blogRepository.delete(blog);

        log.info("Blog deleted: id={}, actorId={}", blogId, actorId);
    }

    @Override
    @Transactional
    public BlogResponse submitForReview(UUID actorId, UUID blogId) {
        Blog blog = findBlog(blogId);

        if (!blog.getAuthor().getId().equals(actorId)) {
            throw new AccessDeniedException("Chỉ author mới gửi được bài duyệt");
        }

        if (blog.getStatus() != BlogStatus.DRAFT && blog.getStatus() != BlogStatus.REJECTED) {
            throw new IllegalArgumentException("Chỉ gửi được bài đang ở trạng thái DRAFT hoặc REJECTED");
        }

        blog.setStatus(BlogStatus.PENDING);
        blog = blogRepository.save(blog);

        log.info("Blog submitted for review: id={}", blogId);
        return toResponse(blog);
    }

    @Override
    @Transactional
    public BlogResponse review(UUID actorId, UUID blogId, ReviewBlogRequest request) {
        Blog blog = findBlog(blogId);

        if (!isStaffOrAdmin(actorId)) {
            throw new AccessDeniedException("Chỉ Staff/Manager/Admin mới duyệt được bài viết");
        }

        String action = request.getAction().trim().toUpperCase(java.util.Locale.ROOT);

        // Guard: APPROVED/REJECTED require blog to be PENDING
        if ((action.equals("APPROVED") || action.equals("REJECTED"))
                && blog.getStatus() != BlogStatus.PENDING) {
            throw new IllegalStateException("Bài viết phải ở trạng thái PENDING mới có thể duyệt");
        }

        BlogStatus newStatus;
        switch (action) {
            case "APPROVED" -> newStatus = BlogStatus.APPROVED;
            case "REJECTED" -> {
                newStatus = BlogStatus.REJECTED;
                if (request.getRejectionReason() == null || request.getRejectionReason().isBlank()) {
                    throw new IllegalArgumentException("Cần lý do từ chối");
                }
                blog.setRejectionReason(request.getRejectionReason().trim());
            }
            case "PUBLISH" -> {
                if (blog.getStatus() != BlogStatus.APPROVED) {
                    throw new IllegalArgumentException("Chỉ publish được bài đã APPROVED");
                }
                newStatus = BlogStatus.PUBLISHED;
            }
            default -> throw new IllegalArgumentException("Hành động không hợp lệ: " + action);
        }

        blog.setStatus(newStatus);

        User reviewer = findUser(actorId);
        blog.setReviewer(reviewer);

        blog = blogRepository.save(blog);

        log.info("Blog reviewed: id={}, status={}, actorId={}", blogId, newStatus, actorId);
        return toResponse(blog);
    }

    @Override
    @Transactional(readOnly = true)
    public BlogListResponse listPublic(BlogStatus status, Pageable pageable) {
        Page<Blog> page;
        // Public endpoint: always PUBLISHED only, ignore any status param
        page = blogRepository.findByStatus(BlogStatus.PUBLISHED, pageable);

        return toListResponse(page);
    }

    @Override
    @Transactional(readOnly = true)
    public BlogListResponse listByAuthor(UUID authorId, Pageable pageable) {
        // Author can see: DRAFT, PENDING, APPROVED, REJECTED, PUBLISHED
        Set<BlogStatus> allowedStatuses = Set.of(
                BlogStatus.DRAFT, BlogStatus.PENDING, BlogStatus.APPROVED,
                BlogStatus.REJECTED, BlogStatus.PUBLISHED
        );

        Page<Blog> page = blogRepository.findByAuthorIdAndAllowedStatus(authorId, allowedStatuses, pageable);

        return toListResponse(page);
    }

    @Override
    @Transactional(readOnly = true)
    public BlogListResponse listAll(Pageable pageable) {
        // Staff/Admin can see all articles with full details
        Page<Blog> page = blogRepository.findAllWithDetails(pageable);
        return toListResponse(page);
    }

    // =========================================================
    // Utility Methods
    // =========================================================

    private User findUser(UUID id) {
        return userRepository.findById(id)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
    }

    private Blog findBlog(UUID id) {
        return blogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết"));
    }

    private boolean isStaffOrAdmin(UUID userId) {
        User user = findUser(userId);
        return Set.of("STAFF", "MANAGER", "ADMIN").contains(user.getRole().name());
    }

    private Blog findBlogBySlug(String slug) {
        return blogRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết"));
    }

    private BlogResponse toResponse(Blog blog) {
        return BlogResponse.builder()
                .id(blog.getId())
                .title(blog.getTitle())
                .slug(blog.getSlug())
                .summary(blog.getSummary())
                .content(blog.getContent())
                .thumbnailUrl(blog.getThumbnailUrl())
                .status(blog.getStatus())
                .author(toUserSummary(blog.getAuthor()))
                .reviewer(blog.getReviewer() != null ? toUserSummary(blog.getReviewer()) : null)
                .rejectionReason(blog.getRejectionReason())
                .createdAt(blog.getCreatedAt())
                .updatedAt(blog.getUpdatedAt())
                .build();
    }

    private BlogResponse.UserSummary toUserSummary(User user) {
        return BlogResponse.UserSummary.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .avatar(user.getAvatar())
                .build();
    }

    private BlogListResponse toListResponse(Page<Blog> page) {
        List<BlogResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return BlogListResponse.builder()
                .content(content)
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .number(page.getNumber())
                .size(page.getSize())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public BlogResponse getBySlug(String slug) {
        Blog blog = findBlogBySlug(slug);
        
        // Only published articles are visible publicly
        if (blog.getStatus() != BlogStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Không tìm thấy bài viết");
        }
        
        return toResponse(blog);
    }

    @Override
    @Transactional(readOnly = true)
    public BlogResponse getBySlugWithAuth(String slug, UUID actorId) {
        Blog blog = findBlogBySlug(slug);
        
        // Check if user can view (published or author or staff/admin)
        if (blog.getStatus() != BlogStatus.PUBLISHED) {
            boolean isAuthor = blog.getAuthor().getId().equals(actorId);
            boolean isStaff = isStaffOrAdmin(actorId);
            if (!isAuthor && !isStaff) {
                throw new AccessDeniedException("Bạn không có quyền xem bài viết này");
            }
        }
        
        return toResponse(blog);
    }
}