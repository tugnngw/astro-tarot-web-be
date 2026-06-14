package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.TarotCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TarotCardRepository extends JpaRepository<TarotCard, UUID> {
}
