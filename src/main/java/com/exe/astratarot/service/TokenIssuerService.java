package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.auth.AuthResponse;
import com.exe.astratarot.domain.entity.User;

public interface TokenIssuerService {
    AuthResponse issueTokens(User user);
}
