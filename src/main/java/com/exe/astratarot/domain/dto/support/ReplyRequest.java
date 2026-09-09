package com.exe.astratarot.domain.dto.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Một lượt trả lời trong luồng ticket — của khách hoặc của nhân viên. */
public record ReplyRequest(
        @NotBlank(message = "Vui lòng nhập nội dung")
        @Size(max = 5000, message = "Nội dung tối đa 5000 ký tự")
        String body
) {}
