package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.payment.PaymentInstructionResponse;
import com.exe.astratarot.domain.dto.payment.PaymentTransactionResponse;
import com.exe.astratarot.domain.entity.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PaymentService {

    /** Sinh (hoặc lấy lại) lệnh thanh toán cho một lịch hẹn (PayOS hoặc chuyển khoản). */
    PaymentInstructionResponse createPaymentIntent(UUID userId, UUID bookingId);

    Page<PaymentTransactionResponse> list(String status, Pageable pageable);

    /** Người trực đối soát sao kê rồi xác nhận. Tiền vào ký quỹ ở bước này. */
    PaymentTransactionResponse confirm(UUID actorId, UUID transactionId);

    PaymentTransactionResponse reject(UUID actorId, UUID transactionId, String reason);

    /**
     * Huỷ lịch: hoàn tiền hoặc xử lý mất cọc. Số tiền hoàn do caller quyết định.
     *
     * <p>amount = 0 thì không gọi cổng thanh toán (chỉ cập nhật trạng thái).
     * Nếu booking ở trạng thái DEPOSIT_PAID và user hủy sớm (≥12h),
     * amount sẽ bằng depositAmount (hoàn cọc). Nếu hủy muộn (<12h),
     * amount = 0 và forfeitedAmount được chuyển cho reader.
     */
    void refund(Booking booking, long amount);

    /**
     * Xử lý mất cọc khi booking bị hủy. Chuyển forfeitedAmount cho reader.
     */
    void forfeitDeposit(Booking booking);

    /**
     * Webhook PayOS (đã verify chữ ký). Body thô từ cổng — SDK tự kiểm checksum.
     */
    void handlePayOsWebhook(Object rawBody);
}
