package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** Ghim trước, rồi mới nhất trước. */
    Page<Notification> findByUserIdOrderByPinnedDescCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.read = true WHERE n.user.id = :userId AND n.read = false")
    int markAllRead(@Param("userId") UUID userId);

    /**
     * Xoá tin đã đọc — bỏ qua tin đã ghim.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId AND n.read = true AND n.pinned = false")
    int deleteAllRead(@Param("userId") UUID userId);

    /**
     * Xoá theo danh sách id của chính user — tin ghim không bị xoá.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM Notification n
            WHERE n.user.id = :userId
              AND n.pinned = false
              AND n.id IN :ids
            """)
    int deleteByIds(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);
}
