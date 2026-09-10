package com.exe.astratarot.domain.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Hướng dẫn thanh toán cho một lịch hẹn.
 *
 * <p>Khi PayOS bật: {@code checkoutUrl} trỏ tới trang PayOS; bank fields vẫn có
 * thể điền từ phản hồi PayOS (STK ảo). Khi chưa bật: chuyển khoản thủ công.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInstructionResponse {
    private UUID transactionId;
    private UUID bookingId;
    private Long amount;
    /** BANK_TRANSFER hoặc PAYOS */
    private String paymentMethod;
    /** Khách gõ đúng chuỗi này vào nội dung chuyển khoản (hoặc mã order PayOS). */
    private String referenceCode;
    private String bankName;
    private String bankAccountNumber;
    private String bankAccountHolder;
    private String transferContent;
    /** Link thanh toán PayOS — FE mở tab mới. Null nếu chỉ chuyển khoản tay. */
    private String checkoutUrl;
    /** VietQR / payload QR từ PayOS (nếu có). */
    private String qrCode;
    private String status;
}
