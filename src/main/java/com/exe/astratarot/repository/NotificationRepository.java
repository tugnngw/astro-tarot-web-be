package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    /**
     * Đánh dấu đã đọc bằng một câu UPDATE thay vì tải hết về rồi set từng cái:
     * người dùng để dồn vài trăm thông báo là chuyện bình thường.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.read = true WHERE n.user.id = :userId AND n.read = false")
    int markAllRead(@Param("userId") UUID userId);

    /**
     * Xoá hẳn những thông báo ĐÃ ĐỌC của một người.
     *
     * <p>Chỉ xoá tin đã đọc. Xoá cả tin chưa đọc là làm mất thứ người ta chưa
     * kịp xem, và không có đường lấy lại.
     *
     * <p>Ràng buộc theo user.id ngay trong câu lệnh chứ không lọc ở tầng
     * service: đây là lệnh xoá, nên phạm vi phải nằm trong chính câu truy vấn.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId AND n.read = true")
    int deleteAllRead(@Param("userId") UUID userId);
}
