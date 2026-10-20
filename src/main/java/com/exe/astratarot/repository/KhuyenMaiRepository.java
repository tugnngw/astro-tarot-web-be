package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.KhuyenMai;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface KhuyenMaiRepository extends JpaRepository<KhuyenMai, UUID> {

    /**
     * Những đợt đang chạy tại thời điểm {@code luc}, mới nhất trước.
     *
     * <p>Trả về danh sách chứ không phải một: hai đợt chồng lịch là chuyện
     * quản trị hoàn toàn có thể nhập (Tết dương và Tết âm sát nhau). Tầng
     * dịch vụ chọn đợt có lợi nhất cho khách, và chọn ở MỘT chỗ.
     */
    @Query("""
            SELECT k FROM KhuyenMai k
            WHERE k.dangBat = TRUE AND k.batDau <= :luc AND k.ketThuc > :luc
            ORDER BY k.batDau DESC
            """)
    List<KhuyenMai> dangChay(@Param("luc") Instant luc);

    List<KhuyenMai> findAllByOrderByBatDauDesc();
}
