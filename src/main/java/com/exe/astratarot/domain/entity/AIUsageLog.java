package com.exe.astratarot.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * AIUsageLog entity tracks all AI service usage (Gemini API calls).
 *
 * Persists metadata about each AI request including:
 * - Token usage (prompt, completion, total)
 * - Estimated cost based on provider pricing
 * - Latency metrics for monitoring
 * - Associated reading or chat session
 *
 * Records are created after successful AI calls and include nullable token/cost
 * fields to gracefully handle scenarios where token data is unavailable.
 *
 * No modification after creation (immutable log entry).
 */
@Entity
@Table(name = "ai_usage_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIUsageLog {

    /**
     * Unique identifier for this usage log entry.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The user who triggered this AI request.
     * Required: every AI call is associated with an authenticated user.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * The TarotReading this AI call was for (if applicable).
     * Nullable: only set for initial reading generation.
     * NULL for chat continuation calls.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reading_id")
    private TarotReading reading;

    /**
     * The ChatSession this AI call was for (if applicable).
     * Nullable: only set for chat continuation calls.
     * NULL for initial reading generation.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_session_id")
    private ChatSession chatSession;

    /**
     * AI service provider name (e.g., "Gemini", "OpenAI").
     * Required: identifies which provider was used for this call.
     */
    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    /**
     * AI model name (e.g., "gemini-1.5-flash", "gpt-4").
     * Required: tracks which model processed this request.
     */
    @Column(name = "model", nullable = false, length = 100)
    private String model;

    /**
     * Number of tokens in the prompt/input.
     * Nullable: may not be provided by the API.
     */
    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    /**
     * Number of tokens in the completion/output.
     * Nullable: may not be provided by the API.
     */
    @Column(name = "completion_tokens")
    private Integer completionTokens;

    /**
     * Total tokens used (prompt + completion).
     * Nullable: calculated field, may not be provided by the API.
     */
    @Column(name = "total_tokens")
    private Integer totalTokens;

    /**
     * Estimated cost in USD for this API call.
     * Calculated based on token counts and provider pricing.
     * Nullable: not calculated if token counts unavailable.
     * Precision: 6 decimal places (supports prices down to $0.000001).
     */
    @Column(name = "estimated_cost_usd", precision = 10, scale = 6)
    private BigDecimal estimatedCostUsd;

    /**
     * Latency of the API call in milliseconds.
     * Nullable: not always measured or available.
     */
    @Column(name = "latency_ms")
    private Integer latencyMs;

    /**
     * Timestamp when this log entry was created.
     * Set automatically by the database at insertion time.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
