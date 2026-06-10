package com.exe.astratarot.domain.dto.auth;

public record OAuthCallbackRequest(String provider, String providerId, String username, String email, String fullName) {
}
