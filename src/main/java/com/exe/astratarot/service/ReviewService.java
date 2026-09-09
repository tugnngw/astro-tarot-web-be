package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.review.CreateReviewRequest;
import com.exe.astratarot.domain.dto.review.ReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReviewService {

    /** Chỉ khách của buổi xem đã hoàn tất mới viết được, và chỉ một lần. */
    ReviewResponse create(UUID userId, UUID bookingId, CreateReviewRequest request);

    /** Đánh giá công khai của một Reader. Khách chưa đăng nhập cũng đọc được. */
    Page<ReviewResponse> listForReader(UUID readerProfileId, Pageable pageable);
}
