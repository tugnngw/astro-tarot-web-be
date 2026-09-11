package com.exe.astratarot.domain.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lượt đặt lịch, nhìn từ cả hai phía.
 *
 * <p>Mang cả thông tin khách lẫn Reader để một DTO dùng chung cho màn "lịch hẹn
 * của tôi" và màn "lịch hẹn của Reader" — hai màn đó chỉ khác nhau ở chỗ hiện
 * tên bên nào, không đáng để tách thành hai kiểu dữ liệu.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private UUID id;

    private UUID readerProfileId;
    /** Tài khoản của Reader. Cần để khách báo cáo đúng người, không phải hồ sơ. */
    private UUID readerUserId;
    private String readerName;
    private String readerAvatar;

    private UUID customerId;
    private String customerName;
    private String customerAvatar;

    private Instant startTime;
    private Instant endTime;
    /** Số phút, suy ra từ start và end để giao diện không phải tự tính. */
    private int durationMinutes;
    private Long totalAmount;

    private String status;
    private String paymentStatus;
    private String cancelReason;

    /** Đã có đánh giá chưa — quyết định hiện nút "Đánh giá" hay điểm đã chấm. */
    private Boolean reviewed;

    /** Ghi chú Reader viết sau buổi xem. Cả khách và Reader đều đọc được. */
    private String readerNote;
    private Instant readerNoteAt;

    private Instant createdAt;
}
