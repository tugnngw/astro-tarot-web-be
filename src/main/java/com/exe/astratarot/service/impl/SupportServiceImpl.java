package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.support.*;
import com.exe.astratarot.domain.entity.SupportTicket;
import com.exe.astratarot.domain.entity.SupportTicketMessage;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.TicketStatus;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.SupportTicketMessageRepository;
import com.exe.astratarot.repository.SupportTicketRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.NotificationService;
import com.exe.astratarot.service.SupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupportServiceImpl implements SupportService {

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public TicketDetailResponse createTicket(UUID requesterId, CreateTicketRequest request) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));

        SupportTicket ticket = SupportTicket.builder()
                .user(requester)
                .subject(request.subject().trim())
                .status(TicketStatus.OPEN)
                .build();

        SupportTicketMessage first = SupportTicketMessage.builder()
                .ticket(ticket)
                .sender(requester)
                .body(request.body().trim())
                .build();
        ticket.getMessages().add(first);

        ticketRepository.save(ticket);
        return toDetail(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketResponse> listMine(UUID requesterId, Pageable pageable) {
        return ticketRepository.findByUserIdOrderByCreatedAtDesc(requesterId, pageable)
                .map(this::toRow);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketResponse> listQueue(TicketStatus status, Pageable pageable) {
        Page<SupportTicket> page = (status == null)
                ? ticketRepository.findAllByOrderByCreatedAtDesc(pageable)
                : ticketRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return page.map(this::toRow);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketDetailResponse getDetail(UUID actorId, boolean staffView, UUID ticketId) {
        SupportTicket ticket = load(ticketId);
        ensureCanSee(ticket, actorId, staffView);
        return toDetail(ticket);
    }

    @Override
    @Transactional
    public TicketDetailResponse reply(UUID actorId, boolean staffView, UUID ticketId, ReplyRequest request) {
        SupportTicket ticket = load(ticketId);
        ensureCanSee(ticket, actorId, staffView);

        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new IllegalArgumentException("Ticket đã đóng, không trả lời thêm được");
        }

        User sender = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
        boolean byStaff = staffView && !ticket.getUser().getId().equals(actorId);

        SupportTicketMessage msg = SupportTicketMessage.builder()
                .ticket(ticket)
                .sender(sender)
                .body(request.body().trim())
                .build();
        ticket.getMessages().add(msg);

        if (byStaff) {
            // Nhân viên trả lời: nhận ticket nếu chưa ai nhận, chuyển sang "chờ
            // khách", và báo cho khách.
            if (ticket.getAssignedTo() == null) {
                ticket.setAssignedTo(sender);
            }
            ticket.setStatus(TicketStatus.PENDING);
            notificationService.push(
                    ticket.getUser(),
                    "SUPPORT_REPLY",
                    "Hỗ trợ đã phản hồi",
                    "Yêu cầu \"" + ticket.getSubject() + "\" vừa có phản hồi mới.",
                    Map.of("ticketId", ticket.getId().toString()));
        } else {
            // Khách bổ sung: kéo ticket về hàng chờ nhân viên, trừ khi đã xong.
            if (ticket.getStatus() != TicketStatus.RESOLVED) {
                ticket.setStatus(TicketStatus.OPEN);
            }
        }

        ticketRepository.save(ticket);
        return toDetail(ticket);
    }

    @Override
    @Transactional
    public TicketResponse updateStatus(UUID staffId, UUID ticketId, TicketStatus status) {
        SupportTicket ticket = load(ticketId);
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
        if (ticket.getAssignedTo() == null) {
            ticket.setAssignedTo(staff);
        }
        ticket.setStatus(status);
        ticketRepository.save(ticket);
        return toRow(ticket);
    }

    // ----------------------------------------------------------------

    private SupportTicket load(UUID ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu hỗ trợ"));
    }

    /** Khách chỉ đụng được ticket của mình; nhân viên (staffView) đụng mọi ticket. */
    private void ensureCanSee(SupportTicket ticket, UUID actorId, boolean staffView) {
        if (staffView) return;
        if (!ticket.getUser().getId().equals(actorId)) {
            throw new AccessDeniedException("Bạn không xem được yêu cầu hỗ trợ này");
        }
    }

    private TicketResponse toRow(SupportTicket t) {
        return new TicketResponse(
                t.getId(),
                t.getSubject(),
                t.getStatus(),
                t.getUser().getFullName(),
                t.getAssignedTo() != null ? t.getAssignedTo().getFullName() : null,
                t.getMessages().size(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }

    private TicketDetailResponse toDetail(SupportTicket t) {
        UUID ownerId = t.getUser().getId();
        List<TicketMessageResponse> messages = t.getMessages().stream()
                .map(m -> new TicketMessageResponse(
                        m.getId(),
                        m.getSender().getId(),
                        m.getSender().getFullName(),
                        !m.getSender().getId().equals(ownerId),
                        m.getBody(),
                        m.getCreatedAt()))
                .toList();
        return new TicketDetailResponse(
                t.getId(),
                t.getSubject(),
                t.getStatus(),
                ownerId,
                t.getUser().getFullName(),
                t.getAssignedTo() != null ? t.getAssignedTo().getFullName() : null,
                t.getCreatedAt(),
                t.getUpdatedAt(),
                messages);
    }
}
