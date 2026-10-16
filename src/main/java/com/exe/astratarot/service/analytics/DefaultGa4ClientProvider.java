package com.exe.astratarot.service.analytics;

import com.exe.astratarot.config.Ga4Properties;
import com.google.analytics.data.v1beta.BetaAnalyticsDataClient;
import com.google.analytics.data.v1beta.BetaAnalyticsDataSettings;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Khởi tạo BetaAnalyticsDataClient với cơ chế nạp credentials an toàn:
 * 1. Từ GA4_CREDENTIALS_JSON (dùng trên cloud/container)
 * 2. Từ GA4_CREDENTIALS_PATH (dùng trên máy local)
 * Nếu không có credentials, app vẫn khởi động bình thường với trạng thái chưa cấu hình.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultGa4ClientProvider implements Ga4ClientProvider {

    private final Ga4Properties properties;
    private BetaAnalyticsDataClient client;
    private boolean configured = false;

    @PostConstruct
    public void init() {
        try {
            GoogleCredentials credentials = null;
            String json = properties.getCredentialsJson();
            String path = properties.getCredentialsPath();

            if (json != null && !json.isBlank()) {
                try (InputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
                    credentials = GoogleCredentials.fromStream(is)
                            .createScoped(Collections.singletonList("https://www.googleapis.com/auth/analytics.readonly"));
                    log.info("GA4 credentials loaded from GA4_CREDENTIALS_JSON");
                }
            } else if (path != null && !path.isBlank()) {
                File file = new File(path);
                if (file.exists() && file.canRead()) {
                    try (InputStream is = new FileInputStream(file)) {
                        credentials = GoogleCredentials.fromStream(is)
                                .createScoped(Collections.singletonList("https://www.googleapis.com/auth/analytics.readonly"));
                        log.info("GA4 credentials loaded from file: {}", file.getName());
                    }
                } else {
                    log.warn("GA4 credentials file not found or unreadable: {}", file.getName());
                }
            }

            if (credentials != null) {
                // newHttpJsonBuilder, KHÔNG phải newBuilder.
                //
                // newBuilder dựng kênh gRPC, và gRPC nạp thư viện SSL viết
                // bằng C (netty-tcnative) ngay lúc khởi tạo. Trên Render thư
                // viện ấy chết hẳn tiến trình:
                //
                //   SIGSEGV ... netty_internal_tcnative_SSLContext_JNI_OnLoad
                //   Exited with status 139
                //
                // Chết ở tầng native thì không `catch` được: cả máy chủ sập
                // theo, không riêng gì phần Analytics. Mà container ở đây
                // chạy với `-Xss512k` và hạn mức bộ nhớ rất chặt, đúng kiểu
                // môi trường thư viện ấy hay vỡ.
                //
                // Bản REST (HTTP/JSON) gọi cùng API, cùng dữ liệu trả về,
                // nhưng đi bằng HTTP client thuần Java — không nạp mảnh C
                // nào, và nhẹ hơn đáng kể trên gói máy nhỏ.
                BetaAnalyticsDataSettings settings = BetaAnalyticsDataSettings.newHttpJsonBuilder()
                        .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                        .build();
                this.client = BetaAnalyticsDataClient.create(settings);
                this.configured = true;
                log.info("GA4 BetaAnalyticsDataClient initialized for property: {}", properties.getPropertyId());
            } else {
                log.info("GA4 credentials not provided — running in unconfigured mode");
                this.configured = false;
            }
        } catch (Exception e) {
            log.error("Failed to initialize GA4 BetaAnalyticsDataClient: {}", e.getMessage());
            this.configured = false;
            this.client = null;
        }
    }

    @Override
    public BetaAnalyticsDataClient getClient() {
        return client;
    }

    @Override
    public boolean isConfigured() {
        return configured && client != null && !client.isShutdown();
    }

    @Override
    public String getPropertyId() {
        return properties.getPropertyId();
    }

    @PreDestroy
    public void close() {
        if (client != null && !client.isShutdown()) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }
}
