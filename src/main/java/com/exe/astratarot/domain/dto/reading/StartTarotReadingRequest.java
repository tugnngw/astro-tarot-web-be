package com.exe.astratarot.domain.dto.reading;

import com.exe.astratarot.domain.dto.astrology.NguoiThuHaiDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for initiating an AI Tarot reading.
 *
 * User ID is derived from the authenticated JWT context and not accepted in the request body.
 * This ensures that a user can only create readings for their own account.
 *
 * Contains question, card drawing parameters, and optional spread name.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartTarotReadingRequest {

    @NotBlank(message = "User question is required")
    private String question;

    @NotNull(message = "Number of cards is required")
    @Min(value = 1, message = "Number of cards must be at least 1")
    @Max(value = 78, message = "Number of cards must not exceed 78")
    private Integer numberOfCards;

    @Builder.Default
    private boolean includeReversed = true;

    private String spreadName;

    /**
     * Người thứ hai, khi người dùng chọn xem đôi (tương hợp, chuyện tình cảm).
     *
     * Null ở lượt xem một người. Đi kèm từng yêu cầu chứ không lưu thành hồ
     * sơ, xem {@link NguoiThuHaiDTO}.
     */
    @Valid
    private NguoiThuHaiDTO nguoiThuHai;
}
