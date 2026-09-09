package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.TarotReading;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TarotReadingRepository extends JpaRepository<TarotReading, UUID> {

    // Lịch sử trải bài của một người dùng, mới nhất trước.
    Page<TarotReading> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
