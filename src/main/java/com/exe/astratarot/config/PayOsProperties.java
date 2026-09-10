package com.exe.astratarot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "payos")
public class PayOsProperties {
    private String clientId = "";
    private String apiKey = "";
    private String checksumKey = "";
    /** URL FE sau khi thanh toán thành công. Trống = FRONTEND_URL/bookings?payment=success */
    private String returnUrl = "";
    /** URL FE khi khách huỷ trên PayOS. Trống = FRONTEND_URL/bookings?payment=cancel */
    private String cancelUrl = "";
    /**
     * URL webhook công khai (Render). Nếu có, app đăng ký với PayOS lúc khởi động.
     * Ví dụ: https://astra-tarot-api.onrender.com/api/v1/payments/payos/webhook
     */
    private String webhookUrl = "";

    public boolean isConfigured() {
        return notBlank(clientId) && notBlank(apiKey) && notBlank(checksumKey);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
