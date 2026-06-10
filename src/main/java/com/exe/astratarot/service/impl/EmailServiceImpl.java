package com.exe.astratarot.service.impl;

import com.exe.astratarot.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from-email}")
    private String fromEmail;

    @Value("${app.mail.from-name:Astra Tarot}")
    private String fromName;

    @Override
    public void sendEmailVerification(String to, String verificationLink) {
        mailSender.send(mimeMessage -> {
            MimeMessageHelper helper =
                    new MimeMessageHelper(mimeMessage, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject("✨ Welcome to Astrotarot - Verify Your Email");
            helper.setText(
                    buildVerificationTemplate(verificationLink),
                    true
            );
        });
    }

    private String buildVerificationTemplate(String verificationLink) {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <title>Email Verification</title>
        </head>

        <body style="
            margin:0;
            padding:0;
            background:#08061d;
            font-family:'Segoe UI',Arial,sans-serif;
        ">

        <table width="100%%" cellpadding="0" cellspacing="0"
               style="padding:40px 0;background:#08061d;">

            <tr>
                <td align="center">

                    <table width="640"
                           cellpadding="0"
                           cellspacing="0"
                           style="
                               background:#12092d;
                               border-radius:18px;
                               overflow:hidden;
                               border:1px solid #2a1d57;
                               box-shadow:0 10px 40px rgba(0,0,0,0.35);
                           ">

                        <!-- HEADER -->

                        <tr>
                            <td style="
                                background:#0b0822;
                                padding:32px;
                                border-bottom:1px solid #2a1d57;
                                text-align:center;
                            ">

                                <div style="
                                    color:#e7c36c;
                                    font-size:42px;
                                    font-weight:300;
                                    letter-spacing:4px;
                                ">
                                    ASTROTAROT
                                </div>

                                <div style="
                                    color:#9f8acb;
                                    margin-top:10px;
                                    font-size:13px;
                                    letter-spacing:2px;
                                ">
                                    DISCOVER YOUR DESTINY
                                </div>

                            </td>
                        </tr>

                        <!-- CONTENT -->

                        <tr>
                            <td style="padding:50px;">

                                <h1 style="
                                    margin-top:0;
                                    color:#ffffff;
                                    text-align:center;
                                    font-size:30px;
                                    font-weight:600;
                                ">
                                    Verify Your Email Address
                                </h1>

                                <p style="
                                    color:#cfc8e8;
                                    line-height:1.9;
                                    font-size:15px;
                                    text-align:center;
                                    margin:24px 0;
                                ">
                                    Welcome to Astrotarot.
                                    Please confirm your email address to secure
                                    your account and begin exploring personalized
                                    tarot readings, spiritual guidance, and insights.
                                </p>

                                <div style="
                                    text-align:center;
                                    margin:40px 0;
                                ">

                                    <a href="%s"
                                       style="
                                            display:inline-block;
                                            background:#e7c36c;
                                            color:#161028;
                                            text-decoration:none;
                                            padding:16px 36px;
                                            border-radius:10px;
                                            font-weight:700;
                                            font-size:15px;
                                       ">
                                        Verify Email
                                    </a>

                                </div>

                                <div style="
                                    background:#1a1238;
                                    border:1px solid #32225f;
                                    border-radius:12px;
                                    padding:18px;
                                    text-align:center;
                                ">

                                    <p style="
                                        margin:0;
                                        color:#bcaee4;
                                        font-size:14px;
                                        line-height:1.8;
                                    ">
                                        If clicking the button doesn't work,
                                        you can copy and paste the verification
                                        link directly from your email client
                                        into your browser.
                                    </p>

                                </div>

                                <p style="
                                    text-align:center;
                                    color:#9d93bc;
                                    font-size:13px;
                                    margin-top:24px;
                                ">
                                    This verification link will expire in 24 hours.
                                </p>

                            </td>
                        </tr>

                        <!-- FOOTER -->

                        <tr>
                            <td style="
                                text-align:center;
                                padding:24px;
                                border-top:1px solid #2a1d57;
                                color:#7f73a7;
                                font-size:12px;
                            ">

                                © 2026 Astrotarot. All rights reserved.

                                <div style="
                                    margin-top:10px;
                                    color:#6f6496;
                                    font-size:11px;
                                ">
                                    This email was sent automatically.
                                    Please do not reply to this message.
                                </div>

                            </td>
                        </tr>

                    </table>

                </td>
            </tr>

        </table>

        </body>
        </html>
        """
                .formatted(verificationLink);
    }
}
