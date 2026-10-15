package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.feedback.AdminFeedbackItemResponse;
import com.exe.astratarot.domain.dto.feedback.FeedbackStatsResponse;
import com.exe.astratarot.service.FeedbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFeedbackControllerTest {

    @Mock
    private FeedbackService feedbackService;

    private AdminFeedbackController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminFeedbackController(feedbackService);
    }

    @Test
    @DisplayName("stats: trả về thông số thống kê tổng hợp cho Quản trị viên")
    void stats() {
        FeedbackStatsResponse expected = new FeedbackStatsResponse(
                30L, 4.8, Map.of(1, 0L, 2, 0L, 3, 1L, 4, 4L, 5, 25L), 9.2, Map.of("TAROT_AI", 30L));
        when(feedbackService.getStats()).thenReturn(expected);

        ResponseEntity<ApiResponse<FeedbackStatsResponse>> res = controller.stats();

        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        assertEquals(30L, res.getBody().getData().totalCount());
        assertEquals(4.8, res.getBody().getData().averageRating());
    }

    @Test
    @DisplayName("list: mặc định phân trang page=0, size=20, giới hạn size tối đa 100")
    void list_defaults_and_clamping() {
        Page<AdminFeedbackItemResponse> emptyPage = new PageImpl<>(List.of());
        when(feedbackService.listAdminFeedback(eq(5), eq("TAROT_AI"), eq("hay"), any(Pageable.class)))
                .thenReturn(emptyPage);

        ResponseEntity<ApiResponse<Page<AdminFeedbackItemResponse>>> res =
                controller.list(5, "TAROT_AI", "hay", 0, 500); // 500 phải bị clamp về 100

        assertEquals(200, res.getStatusCode().value());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(feedbackService).listAdminFeedback(eq(5), eq("TAROT_AI"), eq("hay"), captor.capture());

        Pageable p = captor.getValue();
        assertEquals(0, p.getPageNumber());
        assertEquals(100, p.getPageSize()); // clamped
        assertEquals("createdAt: DESC", p.getSort().toString());
    }
}
