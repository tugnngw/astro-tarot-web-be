package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.user.SendEmailVerificationRequest;
import com.exe.astratarot.domain.dto.user.VerifyEmailRequest;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/email")
@RequiredArgsConstructor
public class UserEmailController {
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/send-verification")
    public ResponseEntity<ApiResponse<Void>> sendVerification(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                              @Valid @RequestBody SendEmailVerificationRequest request) {
        emailVerificationService.sendVerification(userDetails.getUser(), request.email());
        return ResponseEntity.ok(ApiResponse.success("Verification email sent", null));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verify(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
        return ResponseEntity.ok(ApiResponse.success("Email verified", null));
    }
}
