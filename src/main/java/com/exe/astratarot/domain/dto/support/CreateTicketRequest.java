package com.exe.astratarot.domain.dto.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Khách mở một yêu cầu hỗ trợ: tiêu đề + nội dung đầu tiên. */
public record CreateTicketRequest(
        @NotBlank(message = "Vui lòng nhập tiêu đề")
        @Size(max = 200, message = "Tiêu đề tối đa 200 ký tự")
        String subject,

        @NotBlank(message = "Vui lòng nhập nội dung")
        @Size(max = 5000, message = "Nội dung tối đa 5000 ký tự")
        String body
) {}
