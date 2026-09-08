package com.exe.astratarot.service.impl;

import com.exe.astratarot.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from-email}")
    private String fromEmail;

    @Value("${app.mail.from-name:Astra Tarot}")
    private String fromName;

    @Override
    @Async
    public void sendEmailVerification(String to, String verificationLink) {
        send(
                to,
                "✦ Xác minh email của bạn — Astrotarot",
                layout(
                        "Xác minh địa chỉ email",
                        """
                        Chào mừng bạn đến với Astrotarot. Hãy xác nhận địa chỉ email
                        để kích hoạt tài khoản và bắt đầu khám phá những trải bài
                        Tarot được cá nhân hoá.
                        """,
                        "Xác minh email",
                        verificationLink,
                        "Liên kết này hết hạn sau 24 giờ.",
                        "Nếu bạn không đăng ký Astrotarot, hãy bỏ qua email này."
                )
        );
    }

    @Override
    @Async
    public void sendPasswordReset(String to, String resetLink) {
        send(
                to,
                "✦ Đặt lại mật khẩu — Astrotarot",
                layout(
                        "Đặt lại mật khẩu",
                        """
                        Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản
                        của bạn. Bấm nút bên dưới để chọn mật khẩu mới.
                        """,
                        "Đặt lại mật khẩu",
                        resetLink,
                        "Liên kết này hết hạn sau 1 giờ và chỉ dùng được một lần.",
                        "Nếu bạn không yêu cầu đổi mật khẩu, hãy bỏ qua email này — "
                                + "mật khẩu hiện tại vẫn giữ nguyên."
                )
        );
    }

    /**
     * Gửi mail chạy nền (@Async) nên lỗi ở đây KHÔNG ném ngược về controller.
     * Chủ ý: SMTP chậm hoặc chết thì việc đăng ký / yêu cầu đặt lại mật khẩu
     * vẫn phải thành công, người dùng bấm "gửi lại" là xong. Nuốt lỗi nhưng
     * ghi log đầy đủ để còn lần ra khi mail không tới.
     */
    private void send(String to, String subject, String html) {
        try {
            mailSender.send(mimeMessage -> {
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
                helper.setFrom(fromEmail, fromName);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(html, true);
            });
            log.info("Đã gửi mail '{}' tới {}", subject, to);
        } catch (MailException e) {
            log.error("Gửi mail '{}' tới {} thất bại: {}", subject, to, e.getMessage(), e);
        }
    }

    /**
     * Bố cục chung cho mọi email hệ thống.
     *
     * Dùng bảng và style inline vì đó là thứ duy nhất chạy ổn trên các trình
     * đọc mail (Gmail, Outlook lược bỏ phần lớn CSS ngoài). Cũng vì vậy mà
     * không tách được ra file CSS riêng.
     */
    private String layout(
            String heading,
            String bodyText,
            String buttonLabel,
            String buttonLink,
            String expiryNote,
            String footNote
    ) {
        return """
        <!DOCTYPE html>
        <html lang="vi">
        <head><meta charset="UTF-8"><title>%s</title></head>
        <body style="margin:0;padding:0;background:#050410;font-family:'Segoe UI',Arial,sans-serif;">
        <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 0;background:#050410;">
          <tr><td align="center">
            <table width="640" cellpadding="0" cellspacing="0"
                   style="background:#0b0918;border-radius:18px;overflow:hidden;
                          border:1px solid #3a2f13;box-shadow:0 10px 40px rgba(0,0,0,0.5);">

              <tr><td style="background:#070612;padding:32px;border-bottom:1px solid #3a2f13;text-align:center;">
                <div style="color:#e2b75c;font-size:38px;font-weight:300;letter-spacing:5px;">ASTROTAROT</div>
                <div style="color:#9d8fc0;margin-top:10px;font-size:12px;letter-spacing:3px;">KHÁM PHÁ VẬN MỆNH</div>
              </td></tr>

              <tr><td style="padding:48px;">
                <h1 style="margin:0;color:#ffffff;text-align:center;font-size:28px;font-weight:600;">%s</h1>
                <p style="color:#cfc8e8;line-height:1.9;font-size:15px;text-align:center;margin:24px 0;">%s</p>

                <div style="text-align:center;margin:38px 0;">
                  <a href="%s" style="display:inline-block;background:#e2b75c;color:#161028;
                     text-decoration:none;padding:16px 38px;border-radius:10px;font-weight:700;font-size:15px;">%s</a>
                </div>

                <p style="text-align:center;color:#9d93bc;font-size:13px;margin-top:4px;">%s</p>
              </td></tr>

              <tr><td style="text-align:center;padding:24px;border-top:1px solid #3a2f13;color:#7f73a7;font-size:12px;">
                %s
                <div style="margin-top:10px;color:#6f6496;font-size:11px;">
                  © 2026 Astrotarot. Email tự động, vui lòng không trả lời.
                </div>
              </td></tr>

            </table>
          </td></tr>
        </table>
        </body>
        </html>
        """.formatted(
                heading, heading, bodyText, buttonLink, buttonLabel,
                expiryNote, footNote
        );
    }
}
