package com.exe.astratarot.domain.dto.payout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutResponse {
    private UUID id;
    private String readerName;
    private String readerEmail;
    private Long amount;
    private String bankName;
    /** Chỉ bốn số cuối. Màn quản trị hay mở trên máy dùng chung. */
    private String bankAccountMasked;
    private String accountHolder;
    private String status;
    private String rejectReason;
    private Instant requestedAt;
    private Instant processedAt;
}
