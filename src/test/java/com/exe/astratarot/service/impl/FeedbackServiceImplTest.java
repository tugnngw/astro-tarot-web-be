package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.feedback.AdminFeedbackItemResponse;
import com.exe.astratarot.domain.dto.feedback.FeedbackStatsResponse;
import com.exe.astratarot.domain.dto.feedback.SubmitFeedbackRequest;
import com.exe.astratarot.domain.dto.feedback.UserFeedbackResponse;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserFeedback;
import com.exe.astratarot.repository.UserFeedbackRepository;
import com.exe.astratarot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceImplTest {

    @Mock
    private UserFeedbackRepository feedbackRepository;

    @Mock
    private UserRepository userRepository;

    private FeedbackServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FeedbackServiceImpl(feedbackRepository, userRepository);
    }

    @Test
    @DisplayName("Khách ẩn danh gửi phản hồi hợp lệ — tự động suy ra NPS khi không truyền")
    void submit_anonymous_success() {
        SubmitFeedbackRequest request = new SubmitFeedbackRequest(
                "LANDING", 4, null, "Trang web đẹp và mượt mà", "tiktok", "social", "campaign1");

        when(feedbackRepository.save(any(UserFeedback.class))).thenAnswer(inv -> {
            UserFeedback f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            f.setCreatedAt(Instant.now());
            return f;
        });

        UserFeedbackResponse response = service.submit(null, request);

        assertNotNull(response.id());
        assertEquals(4, response.rating());
        assertEquals(8, response.nps()); // 4 * 2
        assertEquals("Trang web đẹp và mượt mà", response.comment());
        assertEquals("LANDING", response.source());
        assertEquals("tiktok", response.utmSource());

        ArgumentCaptor<UserFeedback> captor = ArgumentCaptor.forClass(UserFeedback.class);
        verify(feedbackRepository).save(captor.capture());
        UserFeedback saved = captor.getValue();
        assertNull(saved.getUser());
        assertEquals(8, saved.getNps());
    }

    @Test
    @DisplayName("Khách ẩn danh gửi phản hồi kèm điểm NPS người dùng chọn")
    void submit_anonymous_with_nps() {
        SubmitFeedbackRequest request = new SubmitFeedbackRequest(
                "TAROT_AI", 5, 9, "AI trả lời rất hay", null, null, null);

        when(feedbackRepository.save(any(UserFeedback.class))).thenAnswer(inv -> {
            UserFeedback f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            return f;
        });

        UserFeedbackResponse response = service.submit(null, request);

        assertEquals(5, response.rating());
        assertEquals(9, response.nps()); // Giữ nguyên điểm NPS người dùng chọn
    }

    @Test
    @DisplayName("Thành viên đã đăng nhập gửi phản hồi lần đầu — tạo bản ghi mới gắn đúng user_id")
    void submit_authenticated_first_time() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("cuong").fullName("Nguyễn Cường").build();

        SubmitFeedbackRequest request = new SubmitFeedbackRequest(
                "BOOKING", 5, 10, "Đặt lịch reader rất tiện", null, null, null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(feedbackRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());
        when(feedbackRepository.save(any(UserFeedback.class))).thenAnswer(inv -> inv.getArgument(0));

        UserFeedbackResponse response = service.submit(userId, request);

        assertEquals(5, response.rating());
        assertEquals(10, response.nps());

        ArgumentCaptor<UserFeedback> captor = ArgumentCaptor.forClass(UserFeedback.class);
        verify(feedbackRepository).save(captor.capture());
        UserFeedback saved = captor.getValue();
        assertNotNull(saved.getUser());
        assertEquals(userId, saved.getUser().getId());
    }

    @Test
    @DisplayName("Thành viên gửi lại phản hồi — cập nhật bản ghi cũ, giữ nguyên ID và không tăng tổng số")
    void submit_authenticated_resubmission_updates_existing() {
        UUID userId = UUID.randomUUID();
        UUID existingFeedbackId = UUID.randomUUID();
        User user = User.builder().id(userId).username("cuong").build();

        UserFeedback existing = UserFeedback.builder()
                .id(existingFeedbackId)
                .user(user)
                .source("LANDING")
                .rating(3)
                .nps(6)
                .comment("Ban đầu thấy bình thường")
                .createdAt(Instant.now().minusSeconds(3600))
                .build();

        SubmitFeedbackRequest updateRequest = new SubmitFeedbackRequest(
                "TAROT_AI", 5, null, "Dùng thêm thì thấy AI rất chuẩn", "facebook", "cpc", "retarget");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(feedbackRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(existing));
        when(feedbackRepository.save(any(UserFeedback.class))).thenAnswer(inv -> inv.getArgument(0));

        UserFeedbackResponse response = service.submit(userId, updateRequest);

        assertEquals(existingFeedbackId, response.id());
        assertEquals(5, response.rating());
        assertEquals(10, response.nps()); // 5 * 2
        assertEquals("Dùng thêm thì thấy AI rất chuẩn", response.comment());
        assertEquals("TAROT_AI", response.source());
        assertEquals("facebook", response.utmSource());
        assertNotNull(response.updatedAt());

        // Kiểm tra đúng bản ghi cũ được cập nhật
        verify(feedbackRepository, times(1)).save(existing);
        assertEquals(5, existing.getRating());
        assertEquals(10, existing.getNps());
        assertEquals("Dùng thêm thì thấy AI rất chuẩn", existing.getComment());
    }

    @Test
    @DisplayName("Từ chối khi số sao nhỏ hơn 1 hoặc lớn hơn 5")
    void submit_rating_validation() {
        SubmitFeedbackRequest tooLow = new SubmitFeedbackRequest("GENERAL", 0, 0, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.submit(null, tooLow));

        SubmitFeedbackRequest tooHigh = new SubmitFeedbackRequest("GENERAL", 6, 10, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.submit(null, tooHigh));

        SubmitFeedbackRequest nullRating = new SubmitFeedbackRequest("GENERAL", null, 5, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.submit(null, nullRating));
    }

    @Test
    @DisplayName("Từ chối khi nguồn (source) không nằm trong danh mục cho phép")
    void submit_source_validation() {
        SubmitFeedbackRequest invalidSource = new SubmitFeedbackRequest("HACK_SOURCE", 5, 10, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.submit(null, invalidSource));
    }

    @Test
    @DisplayName("hasSubmitted kiểm tra chính xác trạng thái nộp của người dùng")
    void hasSubmitted() {
        UUID userId = UUID.randomUUID();
        when(feedbackRepository.countByUserId(userId)).thenReturn(1L);
        assertTrue(service.hasSubmitted(userId));

        when(feedbackRepository.countByUserId(userId)).thenReturn(0L);
        assertFalse(service.hasSubmitted(userId));

        assertFalse(service.hasSubmitted(null));
    }

    @Test
    @DisplayName("getMyFeedback trả về thông tin phản hồi của tài khoản")
    void getMyFeedback() {
        UUID userId = UUID.randomUUID();
        UserFeedback feedback = UserFeedback.builder()
                .id(UUID.randomUUID())
                .rating(5)
                .nps(10)
                .comment("Rất hài lòng")
                .source("TAROT_AI")
                .createdAt(Instant.now())
                .build();

        when(feedbackRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(feedback));

        Optional<UserFeedbackResponse> res = service.getMyFeedback(userId);
        assertTrue(res.isPresent());
        assertEquals(5, res.get().rating());
        assertEquals("Rất hài lòng", res.get().comment());

        assertTrue(service.getMyFeedback(null).isEmpty());
    }

    @Test
    @DisplayName("Thống kê khi CSDL hoàn toàn rỗng — đảm bảo không lỗi chia cho 0 và đủ 5 mức sao")
    void getStats_empty_dataset() {
        when(feedbackRepository.count()).thenReturn(0L);
        when(feedbackRepository.getAverageRating()).thenReturn(null);
        when(feedbackRepository.getAverageNps()).thenReturn(null);
        when(feedbackRepository.countByRatingGroup()).thenReturn(List.of());
        when(feedbackRepository.countBySourceGroup()).thenReturn(List.of());

        FeedbackStatsResponse stats = service.getStats();

        assertEquals(0L, stats.totalCount());
        assertEquals(0.0, stats.averageRating());
        assertNull(stats.averageNps());
        assertEquals(5, stats.ratingDistribution().size());
        assertEquals(0L, stats.ratingDistribution().get(1));
        assertEquals(0L, stats.ratingDistribution().get(2));
        assertEquals(0L, stats.ratingDistribution().get(3));
        assertEquals(0L, stats.ratingDistribution().get(4));
        assertEquals(0L, stats.ratingDistribution().get(5));
    }

    @Test
    @DisplayName("Thống kê có dữ liệu — làm tròn 2 chữ số thập phân và phân bổ chính xác")
    void getStats_populated_dataset() {
        when(feedbackRepository.count()).thenReturn(20L);
        when(feedbackRepository.getAverageRating()).thenReturn(4.6666666);
        when(feedbackRepository.getAverageNps()).thenReturn(8.75);

        List<Object[]> ratingRows = List.of(
                new Object[]{5, 15L},
                new Object[]{4, 4L},
                new Object[]{3, 1L}
        );
        when(feedbackRepository.countByRatingGroup()).thenReturn(ratingRows);

        List<Object[]> sourceRows = List.of(
                new Object[]{"TAROT_AI", 12L},
                new Object[]{"BOOKING", 5L},
                new Object[]{"GENERAL", 3L}
        );
        when(feedbackRepository.countBySourceGroup()).thenReturn(sourceRows);

        FeedbackStatsResponse stats = service.getStats();

        assertEquals(20L, stats.totalCount());
        assertEquals(4.67, stats.averageRating()); // Làm tròn HALF_UP
        assertEquals(8.75, stats.averageNps());

        // Phân bổ sao
        assertEquals(0L, stats.ratingDistribution().get(1));
        assertEquals(0L, stats.ratingDistribution().get(2));
        assertEquals(1L, stats.ratingDistribution().get(3));
        assertEquals(4L, stats.ratingDistribution().get(4));
        assertEquals(15L, stats.ratingDistribution().get(5));

        // Phân bổ nguồn
        assertEquals(12L, stats.bySource().get("TAROT_AI"));
        assertEquals(5L, stats.bySource().get("BOOKING"));
        assertEquals(3L, stats.bySource().get("GENERAL"));
        assertEquals(0L, stats.bySource().get("LANDING"));
    }

    @Test
    @DisplayName("Không lọc từ khoá thì truyền thẳng null, KHÔNG dựng chuỗi rỗng")
    void listAdminFeedback_khongTuKhoa() {
        // Chỗ này từng làm production trả 500: truy vấn cũ ghép
        // LIKE LOWER(CONCAT('%', :keyword, '%')), và một null nằm giữa hai
        // hằng chuỗi thì Postgres không suy ra nổi kiểu tham số. Mở tab mà
        // không gõ gì vào ô tìm kiếm là hỏng; gõ một chữ lại chạy.
        //
        // Nay chuỗi so khớp dựng sẵn ở tầng service, còn "không lọc" nghĩa là
        // null đi xuống một phép so sánh bình thường.
        Pageable pageable = PageRequest.of(0, 20);
        when(feedbackRepository.findAdminFeedback(isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listAdminFeedback(null, null, null, pageable);

        verify(feedbackRepository).findAdminFeedback(isNull(), isNull(), isNull(), eq(pageable));
    }

    @Test
    @DisplayName("Từ khoá được hạ chữ thường và bọc dấu % trước khi xuống truy vấn")
    void listAdminFeedback_dungSanChuoiSoKhop() {
        Pageable pageable = PageRequest.of(0, 20);
        when(feedbackRepository.findAdminFeedback(isNull(), isNull(), eq("%hài lòng%"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listAdminFeedback(null, null, "  Hài Lòng  ", pageable);

        verify(feedbackRepository)
                .findAdminFeedback(isNull(), isNull(), eq("%hài lòng%"), eq(pageable));
    }

    @Test
    @DisplayName("Phân trang danh sách phản hồi cho Quản trị viên — phân biệt khách ẩn danh và thành viên")
    void listAdminFeedback() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("lan_reader")
                .fullName("Trần Thị Lan")
                .email("lan@astrotarot.demo")
                .build();

        UserFeedback authFb = UserFeedback.builder()
                .id(UUID.randomUUID())
                .user(user)
                .rating(5)
                .nps(10)
                .comment("Hệ thống rất tốt")
                .source("TAROT_AI")
                .createdAt(Instant.now())
                .build();

        UserFeedback anonFb = UserFeedback.builder()
                .id(UUID.randomUUID())
                .user(null)
                .rating(4)
                .nps(8)
                .comment("Ẩn danh góp ý")
                .source("LANDING")
                .createdAt(Instant.now())
                .build();

        Page<UserFeedback> page = new PageImpl<>(List.of(authFb, anonFb));
        Pageable pageable = PageRequest.of(0, 10);

        when(feedbackRepository.findAdminFeedback(eq(5), eq("TAROT_AI"), eq("%tốt%"), eq(pageable)))
                .thenReturn(page);

        Page<AdminFeedbackItemResponse> result = service.listAdminFeedback(5, "tarot_ai", "tốt", pageable);

        assertEquals(2, result.getTotalElements());

        AdminFeedbackItemResponse item1 = result.getContent().get(0);
        assertFalse(item1.isAnonymous());
        assertEquals("lan_reader", item1.username());
        assertEquals("Trần Thị Lan", item1.userFullName());
        assertEquals("lan@astrotarot.demo", item1.userEmail());
        assertEquals(5, item1.rating());

        AdminFeedbackItemResponse item2 = result.getContent().get(1);
        assertTrue(item2.isAnonymous());
        assertNull(item2.username());
        assertNull(item2.userFullName());
        assertNull(item2.userEmail());
        assertEquals(4, item2.rating());
    }

    @Test
    @DisplayName("Nhiều luồng đồng thời gửi cho cùng 1 tài khoản — đảm bảo chỉ cập nhật 1 bản ghi duy nhất")
    void submit_concurrent_requests_for_same_user() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("concurrent_user").build();

        java.util.concurrent.atomic.AtomicReference<UserFeedback> store = new java.util.concurrent.atomic.AtomicReference<>(null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(feedbackRepository.findFirstByUserIdOrderByCreatedAtDesc(userId))
                .thenAnswer(inv -> Optional.ofNullable(store.get()));
        when(feedbackRepository.save(any(UserFeedback.class)))
                .thenAnswer(inv -> {
                    UserFeedback f = inv.getArgument(0);
                    if (f.getId() == null) {
                        f.setId(UUID.randomUUID());
                        f.setCreatedAt(Instant.now());
                    }
                    store.set(f);
                    return f;
                });

        int threadCount = 10;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger();

        for (int i = 1; i <= threadCount; i++) {
            final int star = (i % 5) + 1;
            executor.submit(() -> {
                try {
                    SubmitFeedbackRequest req = new SubmitFeedbackRequest(
                            "GENERAL", star, null, "Concurrent test " + star, null, null, null);
                    service.submit(userId, req);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, successCount.get());
        assertNotNull(store.get());
        assertNotNull(store.get().getId());
        assertEquals(userId, store.get().getUser().getId());
        verify(feedbackRepository, times(threadCount)).save(any(UserFeedback.class));
    }
}
