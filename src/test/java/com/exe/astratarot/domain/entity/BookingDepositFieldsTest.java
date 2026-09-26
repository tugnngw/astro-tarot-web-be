package com.exe.astratarot.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Kiểm tra các trường đặt cọc trên entity Booking.
 *
 * <p>Sau khi thêm hệ thống đặt cọc 50%, entity Booking phải có:
 * depositAmount, remainingAmount, paymentDeadline, forfeitedAmount.
 * Tất cả đều có giá trị mặc định là null (có thể gán null khi chưa đặt cọc).
 */
@DisplayName("Booking entity - deposit payment fields")
class BookingDepositFieldsTest {

    @Test
    @DisplayName("Booking builder accepts all deposit fields")
    void booking_builder_accepts_deposit_fields() {
        UUID bookingId = UUID.randomUUID();
        UUID readerId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.now();

        Booking booking = Booking.builder()
                .id(bookingId)
                .totalAmount(100000L)
                .depositAmount(50000L)
                .remainingAmount(50000L)
                .paymentDeadline(now.plusSeconds(7200))
                .forfeitedAmount(50000L)
                .paymentStatus(com.exe.astratarot.domain.enums.PaymentStatus.DEPOSIT_PAID)
                .build();

        assertAll(
                () -> assertThat(booking.getDepositAmount()).isEqualTo(50000L),
                () -> assertThat(booking.getRemainingAmount()).isEqualTo(50000L),
                () -> assertThat(booking.getForfeitedAmount()).isEqualTo(50000L),
                () -> assertThat(booking.getPaymentStatus()).isEqualTo(com.exe.astratarot.domain.enums.PaymentStatus.DEPOSIT_PAID),
                () -> assertThat(booking.getPaymentDeadline()).isNotNull()
        );
    }

    @Test
    @DisplayName("Booking without deposit fields defaults to null")
    void booking_without_deposit_fields_defaults_to_null() {
        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .totalAmount(100000L)
                .paymentStatus(com.exe.astratarot.domain.enums.PaymentStatus.UNPAID)
                .build();

        assertAll(
                () -> assertThat(booking.getDepositAmount()).isNull(),
                () -> assertThat(booking.getRemainingAmount()).isNull(),
                () -> assertThat(booking.getForfeitedAmount()).isNull(),
                () -> assertThat(booking.getPaymentDeadline()).isNull()
        );
    }

    @Test
    @DisplayName("PaymentPhase enum has correct values")
    void paymentPhase_enum_values() {
        assertThat(com.exe.astratarot.domain.enums.PaymentPhase.DEPOSIT)
                .isNotNull();
        assertThat(com.exe.astratarot.domain.enums.PaymentPhase.REMAINING)
                .isNotNull();
        assertThat(com.exe.astratarot.domain.enums.PaymentPhase.FULL)
                .isNotNull();
        assertThat(com.exe.astratarot.domain.enums.PaymentPhase.values())
                .hasSize(3);
    }
}
