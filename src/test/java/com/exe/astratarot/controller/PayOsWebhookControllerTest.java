package com.exe.astratarot.controller;

import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Mã trạng thái HTTP trả về cho PayOS là một hợp đồng, không phải chi tiết nội
 * bộ: sai một mã là webhook bị từ chối đăng ký, và triệu chứng duy nhất là một
 * dòng WARN lúc khởi động mà không ai đọc.
 *
 * <p>Không dùng Spring context — gọi thẳng phương thức, chạy trong mili giây.
 */
class PayOsWebhookControllerTest {

    private final PaymentService paymentService = mock(PaymentService.class);
    private final PayOsWebhookController controller = new PayOsWebhookController(paymentService);

    @Test
    @DisplayName("Xử lý được thì trả 200 kèm mã 00")
    void thanhCong() {
        doNothing().when(paymentService).handlePayOsWebhook(any());

        ResponseEntity<Map<String, String>> res = controller.webhook(Map.of());

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).containsEntry("code", "00");
    }

    @Test
    @DisplayName("orderCode không khớp giao dịch nào vẫn phải là 200")
    void orderCodeLaVanTra200() {
        // Đây chính là gói tin PayOS gửi thử lúc đăng ký webhook (orderCode
        // 123). Trước đây nó rơi vào nhánh trả 500, nên PayOS kết luận
        // "Webhook url invalid" và webhook chưa bao giờ đăng ký được.
        doThrow(new ResourceNotFoundException("Không tìm thấy giao dịch PayOS orderCode=123"))
                .when(paymentService).handlePayOsWebhook(any());

        ResponseEntity<Map<String, String>> res = controller.webhook(Map.of());

        assertThat(res.getStatusCode().value())
                .as("5xx làm PayOS từ chối đăng ký webhook")
                .isEqualTo(200);
        assertThat(res.getBody()).containsEntry("code", "01");
    }

    @Test
    @DisplayName("Chữ ký sai hay gói tin dị dạng cũng 200 — gửi lại cũng thế thôi")
    void chuKySaiVanTra200() {
        doThrow(new IllegalArgumentException("Webhook PayOS thiếu orderCode"))
                .when(paymentService).handlePayOsWebhook(any());

        assertThat(controller.webhook(Map.of()).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("Database trục trặc thì mới 500, để PayOS gửi lại")
    void loiDatabaseThiTra500() {
        // Trường hợp duy nhất đáng trả 5xx: mất một webhook thanh toán là mất
        // tiền thật của một Reader, nên phải để PayOS thử lại.
        doThrow(new QueryTimeoutException("statement timeout"))
                .when(paymentService).handlePayOsWebhook(any());

        ResponseEntity<Map<String, String>> res = controller.webhook(Map.of());

        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody()).containsEntry("code", "99");
    }
}
