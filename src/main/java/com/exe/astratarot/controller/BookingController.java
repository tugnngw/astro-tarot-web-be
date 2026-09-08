package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.booking.BookingResponse;
import com.exe.astratarot.domain.dto.booking.CancelBookingRequest;
import com.exe.astratarot.domain.dto.booking.CreateBookingRequest;
import com.exe.astratarot.domain.dto.booking.SlotResponse;
import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.review.CreateReviewRequest;
import com.exe.astratarot.domain.dto.review.ReviewResponse;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.BookingService;
import com.exe.astratarot.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Đặt lịch với Reader.
 *
 * <p>Không có endpoint nào nhận userId từ đường dẫn: người gọi luôn là chính họ,
 * lấy từ token. Ai được xem hay đổi một lịch hẹn thì tầng service tự kiểm tra
 * theo quan hệ với lịch hẹn đó, chứ không theo vai trò — một Reader không được
 * đụng vào lịch hẹn của Reader khác dù cùng vai trò STAFF.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final ReviewService reviewService;

    // ---------- Công khai ----------

    /** Khung giờ còn trống. Khách chưa đăng nhập cũng xem được để quyết định có đăng ký hay không. */
    @GetMapping("/readers/{readerProfileId}/slots")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<List<SlotResponse>>> slots(
            @PathVariable UUID readerProfileId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "30") int duration) {
        return ResponseEntity.ok(ApiResponse.success(
                bookingService.availableSlots(readerProfileId, date, duration)));
    }

    @GetMapping("/readers/{readerProfileId}/reviews")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> readerReviews(
            @PathVariable UUID readerProfileId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.listForReader(readerProfileId, PageRequest.of(page, size))));
    }

    // ---------- Khách đặt lịch ----------

    @PostMapping("/bookings")
    @PreAuthorize("hasAuthority('USER_BASIC')")
    public ResponseEntity<ApiResponse<BookingResponse>> create(
            @AuthenticationPrincipal CustomUserDetails me,
            @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã gửi yêu cầu đặt lịch",
                bookingService.create(me.getUser().getId(), request)));
    }

    @GetMapping("/bookings/me")
    @PreAuthorize("hasAuthority('USER_BASIC')")
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> myBookings(
            @AuthenticationPrincipal CustomUserDetails me,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.listForCustomer(
                me.getUser().getId(), status, PageRequest.of(page, size))));
    }

    // ---------- Reader nhận lịch ----------

    @GetMapping("/bookings/reader")
    @PreAuthorize("hasAuthority('READER_MANAGE_PROFILE')")
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> readerBookings(
            @AuthenticationPrincipal CustomUserDetails me,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.listForReader(
                me.getUser().getId(), status, PageRequest.of(page, size))));
    }

    @PatchMapping("/bookings/{id}/confirm")
    @PreAuthorize("hasAuthority('READER_MANAGE_PROFILE')")
    public ResponseEntity<ApiResponse<BookingResponse>> confirm(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Đã nhận lịch",
                bookingService.confirm(me.getUser().getId(), id)));
    }

    @PatchMapping("/bookings/{id}/complete")
    @PreAuthorize("hasAuthority('READER_MANAGE_PROFILE')")
    public ResponseEntity<ApiResponse<BookingResponse>> complete(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Đã hoàn tất buổi xem",
                bookingService.complete(me.getUser().getId(), id)));
    }

    // ---------- Cả hai bên ----------

    @GetMapping("/bookings/{id}")
    @PreAuthorize("hasAuthority('USER_BASIC')")
    public ResponseEntity<ApiResponse<BookingResponse>> get(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.get(me.getUser().getId(), id)));
    }

    @PatchMapping("/bookings/{id}/cancel")
    @PreAuthorize("hasAuthority('USER_BASIC')")
    public ResponseEntity<ApiResponse<BookingResponse>> cancel(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) CancelBookingRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã huỷ lịch hẹn", bookingService.cancel(
                me.getUser().getId(), id, request == null ? null : request.getReason())));
    }

    @PostMapping("/bookings/{id}/review")
    @PreAuthorize("hasAuthority('USER_BASIC')")
    public ResponseEntity<ApiResponse<ReviewResponse>> review(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID id,
            @Valid @RequestBody CreateReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cảm ơn bạn đã đánh giá",
                reviewService.create(me.getUser().getId(), id, request)));
    }
}
