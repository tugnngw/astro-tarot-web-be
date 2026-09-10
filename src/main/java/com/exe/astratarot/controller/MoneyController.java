package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.payment.PaymentInstructionResponse;
import com.exe.astratarot.domain.dto.payment.PaymentTransactionResponse;
import com.exe.astratarot.domain.dto.payment.RejectRequest;
import com.exe.astratarot.domain.dto.payout.CreatePayoutRequest;
import com.exe.astratarot.domain.dto.payout.EscrowSummaryResponse;
import com.exe.astratarot.domain.dto.payout.PayoutResponse;
import com.exe.astratarot.domain.dto.report.CreateReportRequest;
import com.exe.astratarot.domain.dto.report.HandleReportRequest;
import com.exe.astratarot.domain.dto.report.ReportResponse;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.PaymentService;
import com.exe.astratarot.service.PayoutService;
import com.exe.astratarot.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Tiền và báo cáo vi phạm.
 *
 * <p>Ba nhóm nằm chung một controller vì cùng một chuỗi trách nhiệm: khách trả
 * tiền, tiền nằm ở ký quỹ, Reader rút ra, và khi có tranh chấp thì báo cáo là
 * đường để đòi lại. Tách thành ba file sẽ làm mất mạch đó.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MoneyController {

    private final PaymentService paymentService;
    private final PayoutService payoutService;
    private final ReportService reportService;

    // =========================================================
    // Khách trả tiền
    // =========================================================

    @PostMapping("/bookings/{bookingId}/payment")
    @PreAuthorize("hasAuthority('USER_BASIC')")
    public ResponseEntity<ApiResponse<PaymentInstructionResponse>> pay(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID bookingId) {
        PaymentInstructionResponse instruction =
                paymentService.createPaymentIntent(me.getUser().getId(), bookingId);
        String msg = "PAYOS".equalsIgnoreCase(instruction.getPaymentMethod())
                ? "Mở link PayOS để thanh toán"
                : "Chuyển khoản theo hướng dẫn bên dưới";
        return ResponseEntity.ok(ApiResponse.success(msg, instruction));
    }

    // =========================================================
    // Đối soát thanh toán (quản trị viên)
    // =========================================================

    @GetMapping("/admin/payments")
    @PreAuthorize("hasAuthority('PAYMENTS_MANAGE')")
    public ResponseEntity<ApiResponse<Page<PaymentTransactionResponse>>> payments(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.list(status, PageRequest.of(page, size))));
    }

    @PatchMapping("/admin/payments/{id}/confirm")
    @PreAuthorize("hasAuthority('PAYMENTS_MANAGE')")
    public ResponseEntity<ApiResponse<PaymentTransactionResponse>> confirmPayment(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Đã xác nhận thanh toán",
                paymentService.confirm(me.getUser().getId(), id)));
    }

    @PatchMapping("/admin/payments/{id}/reject")
    @PreAuthorize("hasAuthority('PAYMENTS_MANAGE')")
    public ResponseEntity<ApiResponse<PaymentTransactionResponse>> rejectPayment(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) RejectRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã đánh dấu không đối soát được",
                paymentService.reject(me.getUser().getId(), id,
                        request == null ? null : request.getReason())));
    }

    // =========================================================
    // Ký quỹ và rút tiền (Reader)
    // =========================================================

    @GetMapping("/me/escrow")
    @PreAuthorize("hasAuthority('PAYOUT_REQUEST')")
    public ResponseEntity<ApiResponse<EscrowSummaryResponse>> myEscrow(
            @AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(ApiResponse.success(payoutService.mySummary(me.getUser().getId())));
    }

    @PostMapping("/me/payouts")
    @PreAuthorize("hasAuthority('PAYOUT_REQUEST')")
    public ResponseEntity<ApiResponse<PayoutResponse>> createPayout(
            @AuthenticationPrincipal CustomUserDetails me,
            @Valid @RequestBody CreatePayoutRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã gửi lệnh rút",
                payoutService.create(me.getUser().getId(), request)));
    }

    @GetMapping("/me/payouts")
    @PreAuthorize("hasAuthority('PAYOUT_REQUEST')")
    public ResponseEntity<ApiResponse<Page<PayoutResponse>>> myPayouts(
            @AuthenticationPrincipal CustomUserDetails me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                payoutService.listMine(me.getUser().getId(), PageRequest.of(page, size))));
    }

    // =========================================================
    // Duyệt lệnh rút (quản trị viên)
    // =========================================================

    @GetMapping("/admin/payouts")
    @PreAuthorize("hasAuthority('PAYOUT_REVIEW')")
    public ResponseEntity<ApiResponse<Page<PayoutResponse>>> payouts(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                payoutService.list(status, PageRequest.of(page, size))));
    }

    @PatchMapping("/admin/payouts/{id}/approve")
    @PreAuthorize("hasAuthority('PAYOUT_REVIEW')")
    public ResponseEntity<ApiResponse<PayoutResponse>> approvePayout(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Đã duyệt lệnh rút",
                payoutService.approve(me.getUser().getId(), id)));
    }

    @PatchMapping("/admin/payouts/{id}/reject")
    @PreAuthorize("hasAuthority('PAYOUT_REVIEW')")
    public ResponseEntity<ApiResponse<PayoutResponse>> rejectPayout(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) RejectRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã từ chối lệnh rút",
                payoutService.reject(me.getUser().getId(), id,
                        request == null ? null : request.getReason())));
    }

    /** Đánh dấu đã chuyển khoản thật. Tách khỏi bước duyệt có chủ ý — xem PayoutServiceImpl. */
    @PatchMapping("/admin/payouts/{id}/paid")
    @PreAuthorize("hasAuthority('PAYOUT_REVIEW')")
    public ResponseEntity<ApiResponse<PayoutResponse>> markPayoutPaid(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Đã ghi nhận chuyển khoản",
                payoutService.markPaid(me.getUser().getId(), id)));
    }

    // =========================================================
    // Báo cáo vi phạm
    // =========================================================

    @PostMapping("/reports")
    @PreAuthorize("hasAuthority('USER_BASIC')")
    public ResponseEntity<ApiResponse<ReportResponse>> report(
            @AuthenticationPrincipal CustomUserDetails me,
            @Valid @RequestBody CreateReportRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã ghi nhận. Chúng tôi sẽ xem xét và báo lại cho bạn.",
                reportService.create(me.getUser().getId(), request)));
    }

    @GetMapping("/admin/reports")
    @PreAuthorize("hasAuthority('REPORT_REVIEW')")
    public ResponseEntity<ApiResponse<Page<ReportResponse>>> reports(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                reportService.list(status, PageRequest.of(page, size))));
    }

    @GetMapping("/admin/reports/pending-count")
    @PreAuthorize("hasAuthority('REPORT_REVIEW')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> pendingReports() {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("count", reportService.pendingCount())));
    }

    @PatchMapping("/admin/reports/{id}/handle")
    @PreAuthorize("hasAuthority('REPORT_REVIEW')")
    public ResponseEntity<ApiResponse<ReportResponse>> handleReport(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID id,
            @Valid @RequestBody HandleReportRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã ghi kết luận",
                reportService.handle(me.getUser().getId(), id, request)));
    }
}
