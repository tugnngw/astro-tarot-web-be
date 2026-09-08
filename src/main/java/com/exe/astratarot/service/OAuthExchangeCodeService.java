package com.exe.astratarot.service;

import java.util.UUID;

public interface OAuthExchangeCodeService {
    String createCode(UUID userId);

    UUID exchange(String code);
}
