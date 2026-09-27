package com.exe.astratarot.domain.dto.payment;

import com.exe.astratarot.domain.enums.PaymentPhase;

/**
 * Khách chọn trả cọc hay trả hết. Bỏ trống thì máy chủ tự chọn như bản cũ
 * (còn hơn 12 tiếng thì cọc, sát giờ thì trả hết).
 */
public record CreatePaymentRequest(PaymentPhase phase) {}
