package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.payout.CreatePayoutRequest;
import com.exe.astratarot.domain.dto.payout.EscrowSummaryResponse;
import com.exe.astratarot.domain.dto.payout.PayoutResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PayoutService {

    EscrowSummaryResponse mySummary(UUID readerUserId);

    PayoutResponse create(UUID readerUserId, CreatePayoutRequest request);

    Page<PayoutResponse> listMine(UUID readerUserId, Pageable pageable);

    Page<PayoutResponse> list(String status, Pageable pageable);

    PayoutResponse approve(UUID actorId, UUID payoutId);

    PayoutResponse reject(UUID actorId, UUID payoutId, String reason);

    /** Đánh dấu đã chuyển khoản thật. Tách khỏi bước duyệt có chủ ý. */
    PayoutResponse markPaid(UUID actorId, UUID payoutId);
}
