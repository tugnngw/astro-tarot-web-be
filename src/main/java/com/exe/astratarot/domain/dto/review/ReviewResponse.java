package com.exe.astratarot.domain.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {
    private UUID id;
    private UUID bookingId;
    private String authorName;
    private String authorAvatar;
    private Integer rating;
    private String comment;
    private Instant createdAt;
}
