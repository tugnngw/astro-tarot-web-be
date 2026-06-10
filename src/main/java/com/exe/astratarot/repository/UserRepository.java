package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsernameIgnoreCaseAndDeletedAtIsNull(String username);

    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    Optional<User> findByAuthProviderAndProviderIdAndDeletedAtIsNull(AuthProvider authProvider, String providerId);

    Optional<User> findByEmailVerificationToken(String emailVerificationToken);

    boolean existsByUsernameIgnoreCaseAndDeletedAtIsNull(String username);

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);
}
