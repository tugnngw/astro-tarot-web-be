package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.ReaderProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReaderProfileRepository extends JpaRepository<ReaderProfile, UUID> {
    
    Optional<ReaderProfile> findByUserId(UUID userId);
    
    boolean existsByUserId(UUID userId);

    List<ReaderProfile> findAllByVerifiedAtIsNotNull();
}