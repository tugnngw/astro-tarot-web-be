package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.ReaderUnavailableDate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ReaderUnavailableDateRepository extends JpaRepository<ReaderUnavailableDate, UUID> {
    List<ReaderUnavailableDate> findByReaderId(UUID readerId);
    boolean existsByReaderIdAndUnavailableDate(UUID readerId, LocalDate date);
}