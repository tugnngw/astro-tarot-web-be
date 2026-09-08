package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    Optional<UserSession> findByRefreshTokenHashAndRevokedFalse(String refreshTokenHash);

    /** Mọi phiên còn hiệu lực của một user — dùng để thu hồi khi đổi mật khẩu. */
    java.util.List<UserSession> findByUserIdAndRevokedFalse(java.util.UUID userId);
}
