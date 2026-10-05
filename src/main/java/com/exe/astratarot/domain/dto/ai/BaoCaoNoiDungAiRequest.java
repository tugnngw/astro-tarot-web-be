package com.exe.astratarot.domain.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Báo một lời giải Tarot do AI sinh là không ổn.
 *
 * @param readingId lượt trải bài bị báo
 * @param lyDo      SAI_LECH, XUC_PHAM, NGUY_HIEM hoặc KHAC
 * @param moTa      người dùng tự mô tả thêm, không bắt buộc
 */
public record BaoCaoNoiDungAiRequest(
        @NotNull(message = "Thiếu lượt trải bài cần báo")
        UUID readingId,

        @NotBlank(message = "Chọn một lý do")
        String lyDo,

        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
        String moTa
) {
}
