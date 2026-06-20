package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    Page<ChatMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId, Pageable pageable);

    Page<ChatMessage> findBySessionIdAndCreatedAtBeforeOrderByCreatedAtDesc(
            UUID sessionId, Instant before, Pageable pageable);
}