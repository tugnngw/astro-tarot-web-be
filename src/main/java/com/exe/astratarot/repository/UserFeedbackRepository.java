package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.UserFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserFeedbackRepository extends JpaRepository<UserFeedback, UUID> {

    long countByUserId(UUID userId);

    Optional<UserFeedback> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT AVG(f.rating) FROM UserFeedback f WHERE f.rating IS NOT NULL")
    Double getAverageRating();

    @Query("SELECT AVG(f.nps) FROM UserFeedback f")
    Double getAverageNps();

    @Query("SELECT f.rating, COUNT(f) FROM UserFeedback f WHERE f.rating IS NOT NULL GROUP BY f.rating")
    List<Object[]> countByRatingGroup();

    @Query("SELECT f.source, COUNT(f) FROM UserFeedback f GROUP BY f.source")
    List<Object[]> countBySourceGroup();

    /**
     * Danh sách phản hồi cho trang quản trị, lọc theo sao / nguồn / từ khoá.
     *
     * <p>Nhận {@code pattern} đã dựng sẵn (dạng {@code %tu-khoa%}, đã hạ chữ
     * thường) chứ KHÔNG nhận từ khoá trần rồi ghép bằng {@code CONCAT}.
     *
     * <p>Vì sao: bản cũ viết
     * {@code LIKE LOWER(CONCAT('%', :keyword, '%'))}. Khi không lọc từ khoá,
     * một {@code null} bị nhét vào giữa hai hằng chuỗi của {@code concat()} —
     * hàm này nhận kiểu "any", nên Postgres không suy ra nổi kiểu của tham số
     * và ném lỗi ngay ở bước chuẩn bị câu lệnh. Kết quả: mở tab Đánh giá &
     * Góp ý mà không gõ gì vào ô tìm kiếm là nhận 500, gõ một chữ vào thì lại
     * chạy. H2 trong test dễ tính hơn nên không bắt được.
     *
     * <p>Dạng {@code cot LIKE :pattern} thì Postgres suy kiểu từ vế trái,
     * không cần biết tham số là gì.
     */
    @Query("SELECT f FROM UserFeedback f LEFT JOIN f.user u WHERE " +
            "(:rating IS NULL OR f.rating = :rating) AND " +
            "(:source IS NULL OR f.source = :source) AND " +
            "(:pattern IS NULL OR LOWER(f.comment) LIKE :pattern OR " +
            "(u IS NOT NULL AND (" +
            "LOWER(u.fullName) LIKE :pattern OR " +
            "LOWER(u.email) LIKE :pattern OR " +
            "LOWER(u.username) LIKE :pattern)))")
    Page<UserFeedback> findAdminFeedback(
            @Param("rating") Integer rating,
            @Param("source") String source,
            @Param("pattern") String pattern,
            Pageable pageable);

    /**
     * Góp ý không phải dữ liệu gieo sẵn — xem {@link DuLieuThat}.
     *
     * <p>Ở bảng này lọc theo người gửi là KHÔNG đủ: {@code V2_20} gieo 20 góp
     * ý, trong đó 4 hàng cuối để {@code user_id} rỗng cho giống khách vãng
     * lai. Lọc theo khoá chính thì bắt được cả bốn.
     */
    @Query("SELECT COUNT(x) FROM UserFeedback x WHERE " + DuLieuThat.DIEU_KIEN)
    long countThat();
}
