package com.exe.astratarot.domain.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Một giao dịch nhìn từ màn đối soát của quản trị viên. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransactionResponse {
    private UUID id;
    private UUID bookingId;
    private String payerName;
    private String payerEmail;
    private String readerName;
    private Long amount;
    private String paymentMethod;
    private String referenceCode;
    private String status;
    private Instant bookingStartTime;
    private Instant createdAt;
}
