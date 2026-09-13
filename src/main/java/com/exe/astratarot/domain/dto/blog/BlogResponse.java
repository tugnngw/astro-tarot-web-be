package com.exe.astratarot.domain.dto.blog;

import com.exe.astratarot.domain.enums.BlogStatus;
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
public class BlogResponse {
    private UUID id;
    private String title;
    private String slug;
    private String summary;
    private String content;
    private String thumbnailUrl;
    private BlogStatus status;

    private UserSummary author;

    private UserSummary reviewer;
    private String rejectionReason;

    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSummary {
        private UUID id;
        private String username;
        private String fullName;
        private String avatar;
    }
}
