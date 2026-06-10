package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    Optional<UserSession> findByRefreshTokenHashAndRevokedFalse(String refreshTokenHash);
}
