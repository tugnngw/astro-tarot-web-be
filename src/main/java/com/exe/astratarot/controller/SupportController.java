package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.support.CreateTicketRequest;
import com.exe.astratarot.domain.dto.support.ReplyRequest;
import com.exe.astratarot.domain.dto.support.TicketDetailResponse;
import com.exe.astratarot.domain.dto.support.TicketResponse;
import com.exe.astratarot.domain.dto.support.UpdateTicketStatusRequest;
import com.exe.astratarot.domain.enums.TicketStatus;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Hỗ trợ khách.
 *
 * Hai nhóm người dùng, một bộ endpoint: khách thao tác trên ticket của mình,
 * nhân viên (SUPPORT_VIEW) thấy toàn hàng chờ. Cách phân biệt nằm ở cờ
 * {@code staffView} tính từ quyền của người gọi rồi truyền xuống service — chứ
 * không nhân đôi endpoint cho hai vai.
 */
@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    /** Người gọi có quyền của nhân viên hỗ trợ hay không. */
    private boolean isStaff(CustomUserDetails actor) {
        for (GrantedAuthority a : actor.getAuthorities()) {
            if ("SUPPORT_VIEW".equals(a.getAuthority())) return true;
        }
        return false;
    }

    // ---------- Khách ----------

    @PostMapping("/tickets")
    @PreAuthorize("hasAuthority('USER_BASIC')")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> create(
            @AuthenticationPrincipal CustomUserDetails actor,
            @Valid @RequestBody CreateTicketRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã gửi yêu cầu hỗ trợ",
                supportService.createTicket(actor.getUser().getId(), request)));
    }

    @GetMapping("/tickets/mine")
    @PreAuthorize("hasAuthority('USER_BASIC')")
    public ResponseEntity<ApiResponse<Page<TicketResponse>>> mine(
            @AuthenticationPrincipal CustomUserDetails actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                supportService.listMine(actor.getUser().getId(), pageable)));
    }

    // Xem chi tiết: khách xem ticket của mình, nhân viên xem mọi ticket.
    @GetMapping("/tickets/{ticketId}")
    @PreAuthorize("hasAuthority('USER_BASIC')")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> detail(
            @AuthenticationPrincipal CustomUserDetails actor,
            @PathVariable UUID ticketId) {
        return ResponseEntity.ok(ApiResponse.success(
                supportService.getDetail(actor.getUser().getId(), isStaff(actor), ticketId)));
    }

    // Trả lời: cùng endpoint cho khách và nhân viên; service suy vai từ staffView.
    @PostMapping("/tickets/{ticketId}/messages")
    @PreAuthorize("hasAuthority('USER_BASIC')")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> reply(
            @AuthenticationPrincipal CustomUserDetails actor,
            @PathVariable UUID ticketId,
            @Valid @RequestBody ReplyRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã gửi",
                supportService.reply(actor.getUser().getId(), isStaff(actor), ticketId, request)));
    }

    // ---------- Nhân viên ----------

    @GetMapping("/queue")
    @PreAuthorize("hasAuthority('SUPPORT_VIEW')")
    public ResponseEntity<ApiResponse<Page<TicketResponse>>> queue(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(supportService.listQueue(status, pageable)));
    }

    @PatchMapping("/tickets/{ticketId}/status")
    @PreAuthorize("hasAuthority('SUPPORT_RESPOND')")
    public ResponseEntity<ApiResponse<TicketResponse>> updateStatus(
            @AuthenticationPrincipal CustomUserDetails actor,
            @PathVariable UUID ticketId,
            @Valid @RequestBody UpdateTicketStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật trạng thái",
                supportService.updateStatus(actor.getUser().getId(), ticketId, request.status())));
    }
}
