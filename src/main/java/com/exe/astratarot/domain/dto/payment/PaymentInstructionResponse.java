package com.exe.astratarot.domain.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Hướng dẫn chuyển khoản cho một lịch hẹn. Mã tham chiếu chính là nội dung chuyển. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInstructionResponse {
    private UUID transactionId;
    private UUID bookingId;
    private Long amount;
    /** Khách gõ đúng chuỗi này vào nội dung chuyển khoản. */
    private String referenceCode;
    private String bankName;
    private String bankAccountNumber;
    private String bankAccountHolder;
    private String transferContent;
    private String status;
}
