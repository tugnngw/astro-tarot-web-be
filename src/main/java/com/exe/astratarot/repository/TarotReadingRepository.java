package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.TarotReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TarotReadingRepository extends JpaRepository<TarotReading, UUID> {
}
