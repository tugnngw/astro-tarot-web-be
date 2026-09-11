package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.PaymentStatus;
import com.exe.astratarot.repository.BookingRepository;
import com.exe.astratarot.repository.ReportRepository;
import com.exe.astratarot.service.EscrowService;
import com.exe.astratarot.service.NotificationService;
import com.exe.astratarot.service.NotificationTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Chốt MỘT buổi xem, trong giao dịch của riêng nó.
 *
 * <p>Tách khỏi {@link BookingSettlementJob} không phải để cho gọn mà vì cơ chế
 * proxy: gọi một phương thức {@code @Transactional} từ chính lớp đó thì proxy
 * không xen vào được, nên annotation trở thành vô nghĩa. Phải là một bean khác.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingSettlementService {

    private final BookingRepository bookingRepository;
    private final ReportRepository reportRepository;
    private final EscrowService escrowService;
    private final NotificationService notificationService;

    /**
     * @return true nếu đã chốt; false nếu cố ý bỏ qua (đang tranh chấp, hoặc
     *         trạng thái đã đổi kể từ lúc chọn việc).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean chotMotBuoi(UUID bookingId) {
        Booking b = bookingRepository.findById(bookingId).orElse(null);
        if (b == null) return false;

        // Đọc lại trạng thái trong giao dịch này. Giữa lúc chọn việc và lúc
        // chốt, Reader có thể đã tự bấm hoàn tất — chốt lần nữa là nhả tiền
        // hai lần cho cùng một buổi.
        if (b.getStatus() != BookingStatus.CONFIRMED
                || b.getPaymentStatus() != PaymentStatus.PAID) {
            return false;
        }

        // Buổi xem đang bị tố cáo thì giữ nguyên tiền ở phần đang giữ cho tới
        // khi quản lý kết luận. Nhả trước rồi đòi lại sau là đòi từ một cái ví
        // có thể đã rỗng.
        if (reportRepository.existsOpenForBooking(bookingId)) {
            log.info("Bỏ qua booking {}: đang có báo cáo chưa xử lý", bookingId);
            return false;
        }

        b.setStatus(BookingStatus.COMPLETED);
        escrowService.releaseForBooking(b);
        bookingRepository.save(b);

        notificationService.push(b.getReaderProfile().getUser(),
                NotificationTypes.BOOKING_COMPLETED,
                "Buổi xem đã tự động chốt",
                "Buổi xem kết thúc hơn " + BookingSettlementJob.HAN_CHO_GIO
                        + " tiếng mà chưa được đánh dấu hoàn tất, nên hệ thống đã "
                        + "chốt hộ. Tiền đã chuyển sang phần rút được.",
                Map.of("bookingId", bookingId.toString()));
        return true;
    }
}
