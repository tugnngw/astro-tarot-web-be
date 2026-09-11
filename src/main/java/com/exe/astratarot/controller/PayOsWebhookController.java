package com.exe.astratarot.controller;

import com.exe.astratarot.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Webhook PayOS — public, không JWT. Chữ ký được SDK verify bằng checksum key.
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
        } catch (Exception e) {
            log.warn("PayOS webhook lỗi: {}", e.getMessage());
            // Trả 200 + mã lỗi để PayOS không spam retry quá mức khi body sai chữ ký;
            // vẫn log để điều tra. Với lỗi tạm thời (DB), trả 500 để PayOS gửi lại.
            if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
                return ResponseEntity.ok(Map.of("code", "01", "desc", e.getMessage()));
            }
            return ResponseEntity.internalServerError()
                    .body(Map.of("code", "99", "desc", "internal error"));
        }
    }
}
