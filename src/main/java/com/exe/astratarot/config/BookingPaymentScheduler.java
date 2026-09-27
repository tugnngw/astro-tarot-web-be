package com.exe.astratarot.config;

import com.exe.astratarot.service.BookingService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Công việc định kỳ xử lý thanh toán đặt cọc.
 *
 * <p>Phần còn lại không còn bị đòi trước buổi. Khách cọc 50% được đọc xong
 * rồi mới trả nốt — nhắc nằm ở bước Reader đánh dấu hoàn tất, không phải
 * ở hạn 12 tiếng. Việc quét quá hạn rồi tự huỷ mất cọc vì chưa trả nốt
 * đã bỏ, kẻo buổi bị huỷ trước khi Reader kịp đọc.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingPaymentScheduler {

    private final BookingService bookingService;

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
