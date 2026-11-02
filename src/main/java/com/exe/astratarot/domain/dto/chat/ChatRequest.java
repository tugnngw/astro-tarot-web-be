package com.exe.astratarot.domain.dto.chat;

import com.exe.astratarot.domain.dto.astrology.NguoiThuHaiDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for AI chat (astrology-only or astrology + tarot reading).
 *
 * - readingId=null → AI responds using user's astrology profile only
 * - readingId=UUID → AI responds using astrology profile + drawn cards
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "Message is required")
    @Size(max = 2000, message = "Message cannot exceed 2000 characters")
    private String message;

    private UUID readingId;

    @Builder.Default
    private boolean stream = false;

    /**
     * Người thứ hai, khi người dùng chọn xem đôi (tương hợp, chuyện tình cảm).
     *
     * Null ở lượt xem một người. Đi kèm từng yêu cầu chứ không lưu thành hồ
     * sơ, xem {@link NguoiThuHaiDTO}.
     */
    @Valid
    private NguoiThuHaiDTO nguoiThuHai;
}
