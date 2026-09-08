package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.auth.AuthResponse;
import com.exe.astratarot.domain.dto.auth.OAuthExchangeRequest;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.UserStatus;
import com.exe.astratarot.exception.InvalidTokenException;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.OAuthExchangeCodeService;
import com.exe.astratarot.service.TokenIssuerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
public class OAuthExchangeController {
    private final OAuthExchangeCodeService oAuthExchangeCodeService;
    private final UserRepository userRepository;
    private final TokenIssuerService tokenIssuerService;

    @PostMapping("/exchange")
    public ResponseEntity<AuthResponse> exchange(@Valid @RequestBody OAuthExchangeRequest request) {
        UUID userId = oAuthExchangeCodeService.exchange(request.code());
        User user = userRepository.findById(userId)
                .filter(existing -> existing.getDeletedAt() == null)
                .filter(existing -> existing.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new InvalidTokenException("OAuth exchange user not found"));

        return ResponseEntity.ok(tokenIssuerService.issueTokens(user));
    }
}
