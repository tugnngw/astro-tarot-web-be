package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.payment.PaymentInstructionResponse;
import com.exe.astratarot.domain.dto.payment.PaymentTransactionResponse;
import com.exe.astratarot.domain.entity.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PaymentService {

    /** Sinh (hoặc lấy lại) mã chuyển khoản cho một lịch hẹn. */
    PaymentInstructionResponse createPaymentIntent(UUID userId, UUID bookingId);

    Page<PaymentTransactionResponse> list(String status, Pageable pageable);

    /** Người trực đối soát sao kê rồi xác nhận. Tiền vào ký quỹ ở bước này. */
    PaymentTransactionResponse confirm(UUID actorId, UUID transactionId);

    PaymentTransactionResponse reject(UUID actorId, UUID transactionId, String reason);

    /** Huỷ lịch đã trả tiền: gỡ khỏi ký quỹ và đánh dấu chờ hoàn. Không làm gì nếu chưa trả. */
    void refundIfPaid(Booking booking);
}
