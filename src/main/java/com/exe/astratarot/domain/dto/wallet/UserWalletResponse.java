package com.exe.astratarot.domain.dto.wallet;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class UserWalletResponse {
    private UUID id;
    private UUID userId;
    private Long balance;
    private Instant createdAt;
    private Instant updatedAt;
}
