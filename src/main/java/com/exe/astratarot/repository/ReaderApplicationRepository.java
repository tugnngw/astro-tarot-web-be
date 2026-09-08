package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.ReaderApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ReaderApplicationRepository extends JpaRepository<ReaderApplication, UUID> {
    
    Optional<ReaderApplication> findByUserIdAndStatus(UUID userId, ReaderApplication.ApplicationStatus status);
    
    boolean existsByUserIdAndStatus(UUID userId, ReaderApplication.ApplicationStatus status);
}