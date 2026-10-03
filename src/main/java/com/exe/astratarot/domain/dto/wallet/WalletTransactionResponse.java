package com.exe.astratarot.domain.dto.wallet;

import com.exe.astratarot.domain.enums.TransactionStatus;
import com.exe.astratarot.domain.enums.WalletTransactionType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class WalletTransactionResponse {
    private UUID id;
    private WalletTransactionType type;
    private Long amount;
    private Long balanceBefore;
    private Long balanceAfter;
    private TransactionStatus status;
    private String referenceId;
    private String paymentMethod;
    private String description;
    private Instant createdAt;
}
