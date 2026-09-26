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
 *
 * <p>Ba trường đầu để trống được ở tầng builder vì {@code create()} luôn gán
 * chúng. {@code forfeitedAmount} thì KHÔNG: nó là một bộ đếm tiền, cột khai
 * {@code nullable = false}, và chỗ nào cộng dồn vào nó cũng đọc giá trị cũ ra
 * trước. Null ở đó là một lượt đặt lịch đổ ở tầng database, hoặc một phép cộng
 * ném NullPointerException.
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
    @DisplayName("Không khai cọc thì ba trường để trống, riêng tiền mất cọc là 0")
    void booking_without_deposit_fields_defaults_to_null() {
        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .totalAmount(100000L)
                .paymentStatus(com.exe.astratarot.domain.enums.PaymentStatus.UNPAID)
                .build();

        assertAll(
                () -> assertThat(booking.getDepositAmount()).isNull(),
                () -> assertThat(booking.getRemainingAmount()).isNull(),
                // 0, không phải null. Cột khai `nullable = false` và trường
                // có sẵn `= 0L`, nhưng Lombok BỎ QUA giá trị khởi tạo nếu
                // thiếu @Builder.Default — nên trước đây builder ghi null
                // xuống, và lỗi nổ ở tầng database chứ không ở chỗ sai.
                () -> assertThat(booking.getForfeitedAmount()).isZero(),
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
