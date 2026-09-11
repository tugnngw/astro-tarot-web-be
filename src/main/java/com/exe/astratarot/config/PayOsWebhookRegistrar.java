package com.exe.astratarot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.payos.PayOS;

/**
 * Đăng ký webhook với PayOS, sau khi instance này đã thật sự phục vụ được.
 *
 * <p><b>Vì sao không dùng {@code @PostConstruct} như trước.</b> Đăng ký webhook
 * không phải một lời gọi một chiều: PayOS nhận URL rồi <em>gọi ngược</em> lại
 * chính URL đó để kiểm tra, và chỉ chấp nhận nếu nhận được 2xx.
 *
 * <p>{@code @PostConstruct} chạy lúc dựng bean, còn cách lúc Tomcat mở cổng cả
 * phút. Nên cú gọi ngược của PayOS rơi vào <em>instance cũ</em> đang còn phục
 * vụ — nhìn thấy rõ trong log, hai dòng cùng một giây nhưng khác mã instance:
 *
 * <pre>
 *   10:17:43 [j7mms] PayOS webhook lỗi: Không tìm thấy giao dịch orderCode=123
 *   10:17:43 [gn8hz] Không đăng ký được PayOS webhook … status code 500
 * </pre>
 *
 * <p>Hệ quả là bản vá ở {@code PayOsWebhookController} không bao giờ là bản
 * trả lời cú gọi kiểm tra của chính nó — sửa xong vẫn hỏng y như cũ, và nếu
 * chỉ đọc dòng lỗi mà không đọc mã instance thì sẽ tưởng bản vá sai.
 *
 * <p>Nên thử lại theo nhịp thưa cho tới khi được: lượt đầu sau hai phút (Render
 * cần chừng đó để chuyển luồng sang instance mới), rồi mỗi mười phút. Thành
 * công một lần là thôi hẳn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayOsWebhookRegistrar {

    /** Quá số lần này thì URL sai thật, thử nữa chỉ tổ rác log. */
    private static final int TOI_DA_SO_LAN = 6;

    private final PayOsProperties properties;

    private volatile boolean xong = false;
    private int soLanDaThu = 0;

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT10M")
    public void dangKy() {
        if (xong || soLanDaThu >= TOI_DA_SO_LAN) return;
        if (!properties.isConfigured()) {
            xong = true;
            return;
        }

        String webhook = properties.getWebhookUrl();
        if (webhook == null || webhook.isBlank()) {
            log.info("PAYOS_WEBHOOK_URL trống — đăng ký webhook thủ công trên my.payos.vn");
            xong = true;
            return;
        }

        soLanDaThu++;
        try {
            PayOS client = new PayOS(
                    properties.getClientId().trim(),
                    properties.getApiKey().trim(),
                    properties.getChecksumKey().trim());
            client.webhooks().confirm(webhook.trim());
            xong = true;
            log.info("Đã đăng ký PayOS webhook ở lần thử {}: {}", soLanDaThu, webhook.trim());
        } catch (Exception e) {
            if (soLanDaThu >= TOI_DA_SO_LAN) {
                log.error("Đã thử {} lần vẫn không đăng ký được PayOS webhook ({}): {}. "
                                + "Kiểm tra PAYOS_WEBHOOK_URL rồi đăng ký tay trên my.payos.vn.",
                        soLanDaThu, webhook, e.getMessage());
            } else {
                log.warn("Chưa đăng ký được PayOS webhook (lần {}/{}): {}. Sẽ thử lại.",
                        soLanDaThu, TOI_DA_SO_LAN, e.getMessage());
            }
        }
    }
}
