package com.exe.astratarot.domain.enums;

/**
 * Vòng đời một yêu cầu hỗ trợ.
 *
 * OPEN: khách vừa gửi, chưa ai trả lời.
 * PENDING: nhân viên đã trả lời, đang chờ khách phản hồi.
 * RESOLVED: đã giải quyết xong.
 * CLOSED: đóng hẳn, không nhận thêm trao đổi.
 */
public enum TicketStatus {
    OPEN,
    PENDING,
    RESOLVED,
    CLOSED
}
