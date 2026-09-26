package com.exe.astratarot.config;

import com.exe.astratarot.domain.enums.PaymentPhase;
import com.exe.astratarot.domain.enums.PaymentStatus;
import com.exe.astratarot.repository.BookingRepository;
import com.exe.astratarot.service.BookingService;
import com.exe.astratarot.service.PaymentService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Công việc định kỳ xử lý thanh toán đặt cọc.
 *
 * <p>Hai nhiệm vụ:
 * <ol>
 *   <li>Nhắc thanh toán ở T-24h cho booking đang {@code DEPOSIT_PAID}.</li>
 *   <li>Quét booking quá {@code paymentDeadline} mà chưa trả nốt → tự hủy.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingPaymentScheduler {

    private final BookingRepository bookingRepository;
    private final PaymentService paymentService;
    private final BookingService bookingService;

    /** Mỗi 10 phút chạy một lần */
    @Scheduled(fixedRate = 10 * 60 * 1000)
    @Transactional
    public void processOverdueAndRemind() {
        Instant now = Instant.now();
        Instant nextDeadline = now.plus(24, ChronoUnit.HOURS);

        // === 1. Nhắc thanh toán T-24h ===
        try {
            List<UUID> nearDeadline = bookingRepository.findDepositPaidNearDeadline(
                    PaymentStatus.DEPOSIT_PAID, now, nextDeadline);
            for (UUID bookingId : nearDeadline) {
                try {
                    // TODO: Gửi notification nhắc thanh toán
                    // notificationService.push(...)
                    log.info("Scheduled reminder T-24h for booking {}", bookingId);
                } catch (Exception e) {
                    log.warn("Failed to send reminder for booking {}: {}", bookingId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error processing T-24h reminders: {}", e.getMessage());
        }

        // === 2. Quét quá hạn ===
        try {
            List<UUID> overdueIds = bookingRepository.findOverdueDepositPaid(
                    PaymentStatus.DEPOSIT_PAID, now);
            for (UUID bookingId : overdueIds) {
                try {
                    handleOverdueBooking(bookingId);
                } catch (Exception e) {
                    log.error("Failed to process overdue booking {}: {}", bookingId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error processing overdue bookings: {}", e.getMessage());
        }
    }

    /**
     * Xử lý booking quá hạn: tự hủy và áp dụng chính sách mất cọc.
     * Mỗi booking xử lý trong transaction riêng để tránh xung đột.
     */
    @Transactional
    public void handleOverdueBooking(UUID bookingId) {
        try {
            // Gọi cancel với SYSTEM actor → áp dụng mất cọc
            bookingService.cancel(
                    null, // actorId (system)
                    bookingId,
                    "Quá hạn thanh toán. Tự động hủy.",
                    BookingService.ActorType.SYSTEM);
            log.info("Booking {} đã bị hủy do quá hạn thanh toán", bookingId);
        } catch (EntityNotFoundException e) {
            log.info("Booking {} không tìm thấy, bỏ qua", bookingId);
        } catch (IllegalArgumentException e) {
            // Booking đã được huỷ bởi người dùng trước khi job chạy
            if (e.getMessage().contains("đã huỷ")) {
                log.info("Booking {} đã bị huỷ trước đó, bỏ qua", bookingId);
            } else {
                throw e;
            }
        }
    }
}
