package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.payment.PaymentInstructionResponse;
import com.exe.astratarot.domain.dto.wallet.TopupRequest;
import com.exe.astratarot.domain.dto.wallet.UserWalletResponse;
import com.exe.astratarot.domain.dto.wallet.WalletTransactionResponse;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserWalletResponse>> getMyWallet(
            @AuthenticationPrincipal CustomUserDetails me) {
        UserWalletResponse wallet = walletService.getWalletResponse(me.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(wallet));
    }

    @PostMapping("/topup")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentInstructionResponse>> createTopup(
            @AuthenticationPrincipal CustomUserDetails me,
            @Valid @RequestBody TopupRequest request) {
        PaymentInstructionResponse instruction = walletService.createTopupIntent(me.getUser().getId(), request.getAmount());
        return ResponseEntity.ok(ApiResponse.success("Vui lòng quét mã VietQR để nạp tiền vào ví", instruction));
    }

    @GetMapping("/transactions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<WalletTransactionResponse>>> getTransactions(
            @AuthenticationPrincipal CustomUserDetails me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<WalletTransactionResponse> transactions = walletService.getTransactions(
                me.getUser().getId(), PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size))));
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    @PostMapping("/pay-booking/{bookingId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> payBooking(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID bookingId) {
        walletService.payBookingWithWallet(me.getUser().getId(), bookingId);
        return ResponseEntity.ok(ApiResponse.success("Thanh toán lịch hẹn bằng Ví ASTROTAROT thành công", "SUCCESS"));
    }
}
