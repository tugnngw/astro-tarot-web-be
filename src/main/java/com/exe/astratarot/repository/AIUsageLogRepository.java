package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.AIUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for AIUsageLog entity.
 *
 * Provides persistence operations for AI usage tracking records.
 * Supports queries for cost analysis, performance monitoring, and usage analytics.
 */
@Repository
public interface AIUsageLogRepository extends JpaRepository<AIUsageLog, UUID> {

    /**
     * Find all usage logs for a specific user.
     *
     * @param userId the user ID
     * @return list of usage logs for the user, ordered by creation time (newest first)
     */
    List<AIUsageLog> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Find all usage logs for a specific reading.
     *
     * @param readingId the reading ID
     * @return list of usage logs for the reading
     */
    List<AIUsageLog> findByReadingId(UUID readingId);

    /**
     * Find all usage logs for a specific chat session.
     *
     * @param chatSessionId the chat session ID
     * @return list of usage logs for the session
     */
    List<AIUsageLog> findByChatSessionId(UUID chatSessionId);

    /**
     * Find all usage logs created within a time range.
     *
     * @param startTime the start of the time range (inclusive)
     * @param endTime the end of the time range (inclusive)
     * @return list of usage logs created within the range
     */
    List<AIUsageLog> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant startTime, Instant endTime);

    /**
     * Calculate total estimated cost for a specific user.
     *
     * @param userId the user ID
     * @return total estimated cost in USD, or null if no costs recorded
     */
    @Query("SELECT SUM(a.estimatedCostUsd) FROM AIUsageLog a WHERE a.user.id = :userId")
    BigDecimal calculateTotalCostForUser(@Param("userId") UUID userId);

    /**
     * Calculate total estimated cost for a specific user within a time range.
     *
     * @param userId the user ID
     * @param startTime the start of the time range (inclusive)
     * @param endTime the end of the time range (inclusive)
     * @return total estimated cost in USD within the range
     */
    @Query("SELECT SUM(a.estimatedCostUsd) FROM AIUsageLog a WHERE a.user.id = :userId AND a.createdAt BETWEEN :startTime AND :endTime")
    BigDecimal calculateCostForUserInRange(@Param("userId") UUID userId, @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    /**
     * Calculate total tokens used by a specific user.
     *
     * @param userId the user ID
     * @return total tokens used, or null if no token data recorded
     */
    @Query("SELECT SUM(a.totalTokens) FROM AIUsageLog a WHERE a.user.id = :userId")
    Long calculateTotalTokensForUser(@Param("userId") UUID userId);

    /**
     * Count usage logs for a specific user.
     *
     * @param userId the user ID
     * @return number of usage logs
     */
    long countByUserId(UUID userId);

    /** Tổng token mọi lúc (null → 0 ở tầng service). */
    @Query("SELECT COALESCE(SUM(a.totalTokens), 0) FROM AIUsageLog a")
    long sumTotalTokens();

    @Query("SELECT COALESCE(SUM(a.promptTokens), 0) FROM AIUsageLog a")
    long sumPromptTokens();

    @Query("SELECT COALESCE(SUM(a.completionTokens), 0) FROM AIUsageLog a")
    long sumCompletionTokens();

    @Query("SELECT COALESCE(SUM(a.estimatedCostUsd), 0) FROM AIUsageLog a")
    BigDecimal sumEstimatedCostUsd();

    @Query("SELECT COALESCE(SUM(a.totalTokens), 0) FROM AIUsageLog a WHERE a.createdAt >= :since")
    long sumTotalTokensSince(@Param("since") Instant since);

    long countByCreatedAtGreaterThanEqual(Instant since);

    /** Mỗi hàng: [model, tổng token]. */
    @Query("SELECT a.model, COALESCE(SUM(a.totalTokens), 0) FROM AIUsageLog a GROUP BY a.model ORDER BY SUM(a.totalTokens) DESC")
    List<Object[]> sumTotalTokensGroupedByModel();
}
