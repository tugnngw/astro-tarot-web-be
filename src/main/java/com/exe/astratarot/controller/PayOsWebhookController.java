package com.exe.astratarot.controller;

import com.exe.astratarot.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Webhook PayOS — công khai, không JWT. Chữ ký do SDK kiểm bằng checksum key.
 *
 * <p><b>Mã trạng thái HTTP ở đây có nghĩa hẹp hơn bình thường.</b> PayOS đọc nó
 * theo hai cách, và cả hai đều quan trọng:
 * <ul>
 *   <li>Lúc <b>đăng ký</b> webhook, PayOS gọi thử chính URL này. Bất kỳ mã nào
 *       khác 2xx là nó từ chối đăng ký với thông báo "Webhook url invalid".</li>
 *   <li>Lúc <b>chạy thật</b>, 5xx nghĩa là "tôi hỏng, gửi lại giúp" nên PayOS
 *       sẽ thử lại nhiều lần.</li>
 * </ul>
 *
 * <p>Nên 5xx chỉ dành cho đúng một loại tình huống: hỏng tạm thời, gửi lại thì
 * có cơ may thành công. Mọi thứ khác — chữ ký sai, gói tin dị dạng, orderCode
 * không khớp giao dịch nào — đều là chuyện gửi lại mười lần cũng y như vậy, nên
 * báo nhận bằng 200 kèm mã lỗi trong thân phản hồi.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments/payos")
@RequiredArgsConstructor
public class PayOsWebhookController {

    private final PaymentService paymentService;

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> webhook(@RequestBody Object body) {
        try {
            paymentService.handlePayOsWebhook(body);
            return ResponseEntity.ok(Map.of("code", "00", "desc", "success"));

        } catch (DataAccessException | TransactionException e) {
            // Database trục trặc: đúng nghĩa "tôi hỏng, gửi lại giúp". Đây là
            // trường hợp DUY NHẤT đáng trả 5xx — mất một webhook thanh toán là
            // mất tiền thật của một Reader.
            log.error("PayOS webhook hỏng ở tầng dữ liệu, đề nghị PayOS gửi lại", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("code", "99", "desc", "temporary failure, please retry"));

        } catch (Exception e) {
            // Mọi thứ còn lại. Trước đây khối này liệt kê hai kiểu ngoại lệ
            // được trả 200 và ném phần còn lại thành 500 — tức mặc định là
            // "hỏng tạm thời". Mặc định đó sai chiều: chỉ cần SDK đổi kiểu
            // ngoại lệ cho một chữ ký sai là webhook lại bị PayOS coi là URL
            // hỏng, mà chẳng có gì trong mã nguồn này thay đổi cả.
            log.warn("PayOS webhook không xử lý được: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("code", "01",
                    "desc", e.getMessage() == null ? "rejected" : e.getMessage()));
        }
    }
}
