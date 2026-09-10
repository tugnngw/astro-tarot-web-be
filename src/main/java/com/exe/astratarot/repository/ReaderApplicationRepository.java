package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.ReaderApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ReaderApplicationRepository extends JpaRepository<ReaderApplication, UUID> {
    
    Optional<ReaderApplication> findByUserIdAndStatus(UUID userId, ReaderApplication.ApplicationStatus status);
    
    boolean existsByUserIdAndStatus(UUID userId, ReaderApplication.ApplicationStatus status);

    // Đếm hồ sơ Reader theo trạng thái — cho bảng thống kê quản trị.
    long countByStatus(ReaderApplication.ApplicationStatus status);

    // Đơn gần nhất của một người, bất kể trạng thái. findByUserIdAndStatus ở trên
    // chỉ dùng được khi đã biết trạng thái, mà người nộp đơn thì chính là người
    // chưa biết mình đang chờ, được duyệt hay bị từ chối.
    Optional<ReaderApplication> findTopByUserIdOrderByCreatedAtDesc(UUID userId);
}
