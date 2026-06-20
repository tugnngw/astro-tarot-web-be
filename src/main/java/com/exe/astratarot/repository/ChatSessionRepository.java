package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.ChatSession;
import com.exe.astratarot.domain.enums.SessionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    Optional<ChatSession> findByTarotReadingId(UUID readingId);

    Optional<ChatSession> findByUserIdAndTarotReadingId(UUID userId, UUID readingId);

    /**
     * Finds a chat session by reading ID and session type.
     * Used during AI reading creation to prevent duplicate AI sessions.
     *
     * @param readingId the ID of the TarotReading
     * @param sessionType the session type (AI, READER, etc.)
     * @return Optional containing the session if found
     */
    Optional<ChatSession> findByTarotReadingIdAndSessionType(UUID readingId, SessionType sessionType);
}