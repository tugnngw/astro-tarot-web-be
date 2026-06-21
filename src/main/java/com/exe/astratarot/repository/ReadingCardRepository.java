package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.ReadingCard;
import com.exe.astratarot.domain.entity.TarotReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReadingCardRepository extends JpaRepository<ReadingCard, UUID> {
    @Query("select rc from ReadingCard rc join fetch rc.card where rc.reading = :reading")
    List<ReadingCard> findByReading(TarotReading reading);
}
