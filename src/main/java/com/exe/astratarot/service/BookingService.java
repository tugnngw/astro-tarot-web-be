package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.booking.BookingResponse;
import com.exe.astratarot.domain.dto.booking.CreateBookingRequest;
import com.exe.astratarot.domain.dto.booking.SlotResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Đặt lịch với Reader — trụ cột thứ hai của sản phẩm.
 *
 * <p>Bảng bookings và entity Booking đã tồn tại từ lâu nhưng chưa có tầng code
 * nào phía trên. Đây là tầng đó.
 */
public interface BookingService {

    /** Khung giờ còn trống của một Reader trong một ngày. Công khai, khách xem được. */
    List<SlotResponse> availableSlots(UUID readerProfileId, LocalDate date, int durationMinutes);

    BookingResponse create(UUID customerId, CreateBookingRequest request);

    Page<BookingResponse> listForCustomer(UUID customerId, String status, Pageable pageable);

    Page<BookingResponse> listForReader(UUID readerUserId, String status, Pageable pageable);

    BookingResponse get(UUID actorId, UUID bookingId);

    /** Reader nhận lịch. PENDING -> CONFIRMED. */
    BookingResponse confirm(UUID readerUserId, UUID bookingId);

    /** Reader xác nhận đã xem xong. CONFIRMED -> COMPLETED, mở đường cho đánh giá. */
    BookingResponse complete(UUID readerUserId, UUID bookingId);

    /** Cả khách lẫn Reader đều huỷ được, miễn là buổi xem chưa hoàn tất. */
    BookingResponse cancel(UUID actorId, UUID bookingId, String reason);
}
