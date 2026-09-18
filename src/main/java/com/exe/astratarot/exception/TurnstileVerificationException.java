package com.exe.astratarot.exception;

/**
 * Người gọi không kèm token Turnstile hợp lệ ở một endpoint đang bật kiểm tra.
 *
 * <p>Ném ra 400 chứ không phải 401: đây không phải "sai mật khẩu" mà là "chưa
 * chứng minh được bạn là người". Trả 401 sẽ khiến giao diện tưởng phiên hỏng
 * rồi đá người dùng ra ngoài.
 */
public class TurnstileVerificationException extends RuntimeException {
    public TurnstileVerificationException(String message) {
        super(message);
    }
}
