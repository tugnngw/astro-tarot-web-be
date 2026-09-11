package com.exe.astratarot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;

/**
 * PayOS chỉ bật khi đủ 3 key. Thiếu key thì app vẫn chạy với chuyển khoản thủ công.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(PayOsProperties.class)
@RequiredArgsConstructor
public class PayOsConfig {

    private final PayOsProperties properties;

    @Bean
    public PayOsClient payOsClient() {
        if (!properties.isConfigured()) {
            log.info("PayOS chưa cấu hình — thanh toán dùng chuyển khoản thủ công");
            return PayOsClient.disabled();
        }
        PayOS client = new PayOS(
                properties.getClientId().trim(),
                properties.getApiKey().trim(),
                properties.getChecksumKey().trim());
        log.info("PayOS đã bật (client-id={})", mask(properties.getClientId()));
        return new PayOsClient(client);
    }

    // Việc đăng ký webhook đã chuyển sang PayOsWebhookRegistrar.
    //
    // Trước đây nó nằm ngay đây trong một @PostConstruct, và đó chính là lý do
    // nó chưa bao giờ thành công: PayOS gọi ngược lại URL để kiểm tra, mà lúc
    // @PostConstruct chạy thì instance này còn cách lúc mở cổng cả phút, nên
    // cú gọi ngược rơi vào instance CŨ. Xem chú thích đầy đủ ở lớp kia.

    private static String mask(String id) {
        if (id == null || id.length() < 6) {
            return "***";
        }
        return id.substring(0, 4) + "…" + id.substring(id.length() - 2);
    }

    /** Wrapper để inject an toàn khi PayOS chưa bật. */
    public record PayOsClient(PayOS sdk) {
        public static PayOsClient disabled() {
            return new PayOsClient(null);
        }

        public boolean enabled() {
            return sdk != null;
        }
    }
}
