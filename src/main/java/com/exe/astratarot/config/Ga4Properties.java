package com.exe.astratarot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình kết nối Google Analytics 4 (GA4) Reporting API.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ga4")
public class Ga4Properties {
    /**
     * GA4 Property ID (ví dụ: 559041870).
     */
    private String propertyId = "559041870";

    /**
     * Đường dẫn tới tệp Service Account JSON (lưu ngoài repository trên máy local).
     */
    private String credentialsPath = "";

    /**
     * Chuỗi JSON chứa Service Account key (dùng khi deploy container/Render).
     */
    private String credentialsJson = "";

    public boolean hasCredentials() {
        return (credentialsJson != null && !credentialsJson.isBlank())
                || (credentialsPath != null && !credentialsPath.isBlank());
    }
}
