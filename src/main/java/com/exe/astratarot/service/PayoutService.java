package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.payout.CreatePayoutRequest;
import com.exe.astratarot.domain.dto.payout.EscrowSummaryResponse;
import com.exe.astratarot.domain.dto.payout.PayoutResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PayoutService {

    EscrowSummaryResponse mySummary(UUID readerUserId);

    /**
     * Sổ cái ký quỹ của chính mình, mới nhất trước.
     *
     * <p>Bốn con số tổng không trả lời được câu hỏi quan trọng nhất: "vì sao
     * tháng này tôi nhận ít hơn?". Sổ cái thì trả lời được.
     */
    Page<com.exe.astratarot.domain.dto.payout.EscrowTransactionResponse> myLedger(
            UUID readerUserId, Pageable pageable);

    PayoutResponse create(UUID readerUserId, CreatePayoutRequest request);

    Page<PayoutResponse> listMine(UUID readerUserId, Pageable pageable);

    Page<PayoutResponse> list(String status, Pageable pageable);

    PayoutResponse approve(UUID actorId, UUID payoutId);

    PayoutResponse reject(UUID actorId, UUID payoutId, String reason);

    /** Đánh dấu đã chuyển khoản thật. Tách khỏi bước duyệt có chủ ý. */
    PayoutResponse markPaid(UUID actorId, UUID payoutId);
}
