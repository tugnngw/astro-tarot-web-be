package com.exe.astratarot.domain.dto.payout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Một dòng sổ cái ký quỹ, viết cho Reader đọc. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscrowTransactionResponse {
    private UUID id;

    /** HOLD, RELEASE, PENALTY, ... — giao diện tự dịch sang nhãn tiếng Việt. */
    private String kind;

    /** Luôn dương. Hướng tiền nằm ở `kind`, không nhét dấu âm vào số tiền. */
    private Long amount;

    private Long balanceAfter;
    private Long pendingAfter;
    private UUID bookingId;
    private String note;
    private Instant createdAt;
}
