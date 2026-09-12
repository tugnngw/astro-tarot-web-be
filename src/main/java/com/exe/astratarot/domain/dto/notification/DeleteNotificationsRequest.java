package com.exe.astratarot.domain.dto.notification;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Xoá hàng loạt theo id đã chọn. Tin ghim bị bỏ qua phía server. */
public record DeleteNotificationsRequest(
        @NotEmpty List<@NotNull UUID> ids
) {}
