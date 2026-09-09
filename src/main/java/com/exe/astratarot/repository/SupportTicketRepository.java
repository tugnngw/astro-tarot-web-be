package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.SupportTicket;
import com.exe.astratarot.domain.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    // Ticket của một khách, mới nhất trước.
    Page<SupportTicket> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    // Hàng chờ của nhân viên: lọc theo trạng thái, hoặc lấy tất cả khi không lọc.
    Page<SupportTicket> findByStatusOrderByCreatedAtDesc(TicketStatus status, Pageable pageable);
    Page<SupportTicket> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(TicketStatus status);
}
