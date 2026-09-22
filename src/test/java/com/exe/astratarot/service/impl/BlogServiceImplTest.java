package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.blog.ReviewBlogRequest;
import com.exe.astratarot.domain.dto.blog.UpdateBlogRequest;
import com.exe.astratarot.domain.dto.blog.BlogResponse;
import com.exe.astratarot.domain.entity.Blog;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.BlogStatus;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.repository.BlogRepository;
import com.exe.astratarot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlogServiceImplTest {

    @Mock
    private BlogRepository blogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BlogServiceImpl blogService;

    private UUID authorId;
    private UUID staffId;
    private UUID managerId;
    private UUID adminId;
    private UUID blogId;

    private User author;
    private User staff;
    private User manager;
    private User admin;
    private Blog blog;

    @BeforeEach
    void setUp() {
        authorId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        blogId = UUID.randomUUID();

        author = User.builder().id(authorId).username("author").role(UserRole.STAFF).build();
        staff = User.builder().id(staffId).username("staff").role(UserRole.STAFF).build();
        manager = User.builder().id(managerId).username("manager").role(UserRole.MANAGER).build();
        admin = User.builder().id(adminId).username("admin").role(UserRole.ADMIN).build();

        blog = Blog.builder()
                .id(blogId)
                .title("Original Title")
                .slug("original-title")
                .summary("Original Summary")
                .content("Original Content")
                .author(author)
                .status(BlogStatus.DRAFT)
                .build();
    }

    @Test
    @DisplayName("Updating PENDING blog changes status to DRAFT and clears rejectionReason")
    void update_PendingBlog_ChangesStatusToDraft() {
        blog.setStatus(BlogStatus.PENDING);
        blog.setRejectionReason("Previous reason");

        when(blogRepository.findById(blogId)).thenReturn(Optional.of(blog));
        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(blogRepository.save(any(Blog.class))).thenAnswer(i -> i.getArgument(0));

        UpdateBlogRequest request = UpdateBlogRequest.builder()
                .title("Updated Title")
                .build();

        BlogResponse response = blogService.update(authorId, blogId, request);

        assertEquals(BlogStatus.DRAFT, response.getStatus());
        assertEquals("Updated Title", response.getTitle());
        assertNull(response.getRejectionReason());
        verify(blogRepository).save(blog);
    }

    @Test
    @DisplayName("Updating DRAFT blog preserves DRAFT status")
    void update_DraftBlog_PreservesDraftStatus() {
        blog.setStatus(BlogStatus.DRAFT);

        when(blogRepository.findById(blogId)).thenReturn(Optional.of(blog));
        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(blogRepository.save(any(Blog.class))).thenAnswer(i -> i.getArgument(0));

        UpdateBlogRequest request = UpdateBlogRequest.builder()
                .title("New Title")
                .build();

        BlogResponse response = blogService.update(authorId, blogId, request);

        assertEquals(BlogStatus.DRAFT, response.getStatus());
        assertEquals("New Title", response.getTitle());
    }

    @Test
    @DisplayName("Updating REJECTED blog by author throws IllegalArgumentException as expected by existing rules")
    void update_RejectedBlog_ThrowsException() {
        blog.setStatus(BlogStatus.REJECTED);
        blog.setRejectionReason("Needs fixing");

        when(blogRepository.findById(blogId)).thenReturn(Optional.of(blog));
        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));

        UpdateBlogRequest request = UpdateBlogRequest.builder()
                .content("Fixed Content")
                .build();

        assertThrows(IllegalArgumentException.class, () -> blogService.update(authorId, blogId, request));
    }

    @Test
    @DisplayName("STAFF role user attempting review throws AccessDeniedException")
    void review_StaffRole_ThrowsAccessDeniedException() {
        blog.setStatus(BlogStatus.PENDING);

        when(blogRepository.findById(blogId)).thenReturn(Optional.of(blog));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));

        ReviewBlogRequest request = ReviewBlogRequest.builder()
                .action("APPROVED")
                .build();

        assertThrows(AccessDeniedException.class, () -> blogService.review(staffId, blogId, request));
        verify(blogRepository, never()).save(any());
    }

    @Test
    @DisplayName("MANAGER role user can review blog to APPROVED")
    void review_ManagerRole_ApprovesBlog() {
        blog.setStatus(BlogStatus.PENDING);

        when(blogRepository.findById(blogId)).thenReturn(Optional.of(blog));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(blogRepository.save(any(Blog.class))).thenAnswer(i -> i.getArgument(0));

        ReviewBlogRequest request = ReviewBlogRequest.builder()
                .action("APPROVED")
                .build();

        BlogResponse response = blogService.review(managerId, blogId, request);

        assertEquals(BlogStatus.APPROVED, response.getStatus());
        assertEquals(managerId, response.getReviewer().getId());
    }

    @Test
    @DisplayName("ADMIN role user can review blog to REJECTED with reason")
    void review_AdminRole_RejectsBlog() {
        blog.setStatus(BlogStatus.PENDING);

        when(blogRepository.findById(blogId)).thenReturn(Optional.of(blog));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(blogRepository.save(any(Blog.class))).thenAnswer(i -> i.getArgument(0));

        ReviewBlogRequest request = ReviewBlogRequest.builder()
                .action("REJECTED")
                .rejectionReason("Inappropriate content")
                .build();

        BlogResponse response = blogService.review(adminId, blogId, request);

        assertEquals(BlogStatus.REJECTED, response.getStatus());
        assertEquals("Inappropriate content", response.getRejectionReason());
    }

    @Test
    @DisplayName("Submitting DRAFT blog changes status to PENDING")
    void submitForReview_DraftBlog_ChangesToPending() {
        blog.setStatus(BlogStatus.DRAFT);

        when(blogRepository.findById(blogId)).thenReturn(Optional.of(blog));
        when(blogRepository.save(any(Blog.class))).thenAnswer(i -> i.getArgument(0));

        BlogResponse response = blogService.submitForReview(authorId, blogId);

        assertEquals(BlogStatus.PENDING, response.getStatus());
    }
}
