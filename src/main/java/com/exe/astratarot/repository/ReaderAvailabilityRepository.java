package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.ReaderAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ReaderAvailabilityRepository extends JpaRepository<ReaderAvailability, UUID> {
    List<ReaderAvailability> findByReaderId(UUID readerId);
}