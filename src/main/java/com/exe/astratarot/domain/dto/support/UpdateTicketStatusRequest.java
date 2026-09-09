package com.exe.astratarot.domain.dto.support;

import com.exe.astratarot.domain.enums.TicketStatus;
import jakarta.validation.constraints.NotNull;

/** Nhân viên đổi trạng thái xử lý của ticket. */
public record UpdateTicketStatusRequest(
        @NotNull(message = "Vui lòng chọn trạng thái")
        TicketStatus status
) {}
