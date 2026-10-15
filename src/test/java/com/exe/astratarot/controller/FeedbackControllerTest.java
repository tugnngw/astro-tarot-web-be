package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.feedback.SubmitFeedbackRequest;
import com.exe.astratarot.domain.dto.feedback.UserFeedbackResponse;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.FeedbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackControllerTest {

    @Mock
    private FeedbackService feedbackService;

    private FeedbackController controller;

    @BeforeEach
    void setUp() {
        controller = new FeedbackController(feedbackService);
    }

    @Test
    @DisplayName("Khách chưa đăng nhập: userId truyền vào service là null, không gán danh tính giả mạo")
    void submit_anonymous() {
        SubmitFeedbackRequest request = new SubmitFeedbackRequest(
                "LANDING", 5, 10, "Trải nghiệm rất tốt", null, null, null);

        UserFeedbackResponse expected = new UserFeedbackResponse(
                UUID.randomUUID(), 5, 10, "Trải nghiệm rất tốt", "LANDING", null, null, null, Instant.now(), Instant.now());
        when(feedbackService.submit(isNull(), eq(request))).thenReturn(expected);

        ResponseEntity<ApiResponse<UserFeedbackResponse>> res = controller.submit(null, request);

        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        assertEquals(5, res.getBody().getData().rating());
        verify(feedbackService).submit(isNull(), eq(request));
    }

    @Test
    @DisplayName("Thành viên đã đăng nhập: userId lấy từ AuthenticationPrincipal đã xác thực, không từ client")
    void submit_authenticated() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").role(UserRole.USER).build();
        CustomUserDetails me = new CustomUserDetails(user);

        SubmitFeedbackRequest request = new SubmitFeedbackRequest(
                "TAROT_AI", 4, 8, "Ổn", null, null, null);

        UserFeedbackResponse expected = new UserFeedbackResponse(
                UUID.randomUUID(), 4, 8, "Ổn", "TAROT_AI", null, null, null, Instant.now(), Instant.now());
        when(feedbackService.submit(eq(userId), eq(request))).thenReturn(expected);

        ResponseEntity<ApiResponse<UserFeedbackResponse>> res = controller.submit(me, request);

        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        verify(feedbackService).submit(eq(userId), eq(request));
    }

    @Test
    @DisplayName("myStatus: thành viên đã đăng nhập nhận được trạng thái nộp và bản ghi góp ý của mình")
    void myStatus_authenticated() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").role(UserRole.USER).build();
        CustomUserDetails me = new CustomUserDetails(user);

        UserFeedbackResponse myFb = new UserFeedbackResponse(
                UUID.randomUUID(), 5, 10, "Góp ý cũ", "TAROT_AI", null, null, null, Instant.now(), Instant.now());

        when(feedbackService.hasSubmitted(userId)).thenReturn(true);
        when(feedbackService.countAll()).thenReturn(25L);
        when(feedbackService.getMyFeedback(userId)).thenReturn(Optional.of(myFb));

        ResponseEntity<ApiResponse<Map<String, Object>>> res = controller.myStatus(me);

        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        Map<String, Object> data = res.getBody().getData();
        assertEquals(true, data.get("submitted"));
        assertEquals(25L, data.get("total"));
        assertEquals(true, data.get("goalMet"));
        assertEquals(myFb, data.get("myFeedback"));
    }

    @Test
    @DisplayName("myStatus: khách vãng lai nhận submitted = false và myFeedback = null")
    void myStatus_anonymous() {
        when(feedbackService.countAll()).thenReturn(10L);

        ResponseEntity<ApiResponse<Map<String, Object>>> res = controller.myStatus(null);

        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        Map<String, Object> data = res.getBody().getData();
        assertEquals(false, data.get("submitted"));
        assertEquals(10L, data.get("total"));
        assertEquals(false, data.get("goalMet"));
        assertNull(data.get("myFeedback"));
    }
}
