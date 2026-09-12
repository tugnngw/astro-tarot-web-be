package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.UserFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserFeedbackRepository extends JpaRepository<UserFeedback, UUID> {
    long countByUserId(UUID userId);
}
