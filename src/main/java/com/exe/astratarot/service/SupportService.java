package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.support.CreateTicketRequest;
import com.exe.astratarot.domain.dto.support.ReplyRequest;
import com.exe.astratarot.domain.dto.support.TicketDetailResponse;
import com.exe.astratarot.domain.dto.support.TicketResponse;
import com.exe.astratarot.domain.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Hỗ trợ khách: khách mở yêu cầu, nhân viên xử lý.
 *
 * {@code staffView} do controller truyền xuống (tính từ quyền SUPPORT_VIEW),
 * quyết định người gọi được xem/trả lời mọi ticket hay chỉ ticket của mình.
 */
public interface SupportService {

    TicketDetailResponse createTicket(UUID requesterId, CreateTicketRequest request);

    Page<TicketResponse> listMine(UUID requesterId, Pageable pageable);

    Page<TicketResponse> listQueue(TicketStatus status, Pageable pageable);

    TicketDetailResponse getDetail(UUID actorId, boolean staffView, UUID ticketId);

    TicketDetailResponse reply(UUID actorId, boolean staffView, UUID ticketId, ReplyRequest request);

    TicketResponse updateStatus(UUID staffId, UUID ticketId, TicketStatus status);
}
