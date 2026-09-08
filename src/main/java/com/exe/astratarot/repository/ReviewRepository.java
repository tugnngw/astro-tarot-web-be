package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByBookingId(UUID bookingId);

    /**
     * Những booking trong danh sách đã có đánh giá.
     *
     * <p>Một câu hỏi cho cả trang thay vì existsByBookingId cho từng dòng —
     * hai mươi lượt đặt là hai mươi câu truy vấn, và nó chỉ để bật hay tắt một
     * cái nút.
     */
    @Query("SELECT r.booking.id FROM Review r WHERE r.booking.id IN :bookingIds")
    Set<UUID> findReviewedBookingIds(@Param("bookingIds") Iterable<UUID> bookingIds);

    @Query("""
            SELECT r FROM Review r
            JOIN FETCH r.user
            WHERE r.readerProfile.id = :readerProfileId
            ORDER BY r.createdAt DESC
            """)
    Page<Review> findByReaderProfile(@Param("readerProfileId") UUID readerProfileId, Pageable pageable);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.readerProfile.id = :readerProfileId")
    double averageRating(@Param("readerProfileId") UUID readerProfileId);

    long countByReaderProfileId(UUID readerProfileId);
}
