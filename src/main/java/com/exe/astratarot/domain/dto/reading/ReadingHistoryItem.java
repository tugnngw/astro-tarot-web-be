package com.exe.astratarot.domain.dto.reading;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng trong lịch sử trải bài của người dùng.
 *
 * Chỉ mang phần đủ để dựng danh sách: câu hỏi và thời điểm. Nội dung lá bài và
 * lời giải nằm ở màn chi tiết, lấy khi người dùng mở lại một lượt.
 */
public record ReadingHistoryItem(
        UUID id,
        String mainQuestion,
        String sessionType,
        String aiModelUsed,
        Instant createdAt
) {}
