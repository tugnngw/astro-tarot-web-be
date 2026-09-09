package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    /**
     * Nhật ký, lọc theo hành động và theo đối tượng bị tác động.
     *
     * Chuỗi rỗng nghĩa là "không lọc" — cùng lý do với UserRepository.searchForAdmin:
     * JPQL gửi null không kèm kiểu thì Postgres tự suy ra bytea rồi nổ.
     */
    @Query("""
            SELECT l FROM ActivityLog l
            LEFT JOIN FETCH l.user
            WHERE (:action = '' OR l.action = :action)
              AND (:entityType = '' OR l.entityType = :entityType)
            ORDER BY l.createdAt DESC
            """)
    Page<ActivityLog> search(@Param("action") String action,
                             @Param("entityType") String entityType,
                             Pageable pageable);
}
