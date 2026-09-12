package com.exe.astratarot.domain.dto.notification;

import jakarta.validation.constraints.NotNull;

/** Ghim / bỏ ghim một thông báo. */
public record PinNotificationRequest(@NotNull Boolean pinned) {}
