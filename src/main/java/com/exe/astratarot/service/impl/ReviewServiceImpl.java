package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.review.CreateReviewRequest;
import com.exe.astratarot.domain.dto.review.ReviewResponse;
import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.Review;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.BookingRepository;
import com.exe.astratarot.repository.ReaderProfileRepository;
import com.exe.astratarot.repository.ReviewRepository;
import com.exe.astratarot.service.NotificationService;
import com.exe.astratarot.service.NotificationTypes;
import com.exe.astratarot.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final ReaderProfileRepository readerProfileRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public ReviewResponse create(UUID userId, UUID bookingId, CreateReviewRequest request) {
        Booking booking = bookingRepository.findByIdWithParties(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn"));

        // Đánh giá khoá theo booking, và chỉ chính khách của buổi xem đó mới
        // viết được. Đây là thứ duy nhất giữ cho điểm số có nghĩa: bỏ ràng buộc
        // này là ai cũng chấm được điểm cho Reader mình chưa từng gặp.
        if (!booking.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Chỉ khách của buổi xem này mới đánh giá được");
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new IllegalArgumentException("Chỉ đánh giá được sau khi buổi xem hoàn tất");
        }
        if (reviewRepository.existsByBookingId(bookingId)) {
            throw new IllegalArgumentException("Bạn đã đánh giá buổi xem này rồi");
        }

        ReaderProfile reader = booking.getReaderProfile();
        Review review = reviewRepository.save(Review.builder()
                .booking(booking)
                .user(booking.getUser())
                .readerProfile(reader)
                .rating(request.getRating())
                .comment(request.getComment() == null || request.getComment().isBlank()
                        ? null
                        : request.getComment().trim())
                .build());

        recalculateReaderRating(reader);

        notificationService.push(reader.getUser(), NotificationTypes.REVIEW_RECEIVED,
                "Bạn có đánh giá mới",
                booking.getUser().getFullName() + " chấm " + request.getRating() + " sao cho buổi xem.",
                Map.of("bookingId", bookingId.toString()));

        return toResponse(review);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> listForReader(UUID readerProfileId, Pageable pageable) {
        return reviewRepository.findByReaderProfile(readerProfileId, pageable).map(this::toResponse);
    }

    /**
     * Tính lại điểm trung bình từ TOÀN BỘ đánh giá thay vì cộng dồn.
     *
     * Cộng dồn nhanh hơn nhưng sai vĩnh viễn nếu có một đánh giá bị ẩn hay xoá:
     * số đã cộng vào không gỡ ra được. Đọc lại cả bảng thì luôn đúng, và mỗi
     * Reader cũng chỉ có vài trăm đánh giá.
     */
    private void recalculateReaderRating(ReaderProfile reader) {
        double avg = reviewRepository.averageRating(reader.getId());
        long count = reviewRepository.countByReaderProfileId(reader.getId());
        reader.setRating(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        reader.setTotalReviews((int) count);
        readerProfileRepository.save(reader);
    }

    private ReviewResponse toResponse(Review r) {
        return ReviewResponse.builder()
                .id(r.getId())
                .bookingId(r.getBooking().getId())
                .authorName(r.getUser().getFullName())
                .authorAvatar(r.getUser().getAvatar())
                .rating(r.getRating())
                .comment(r.getComment())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
