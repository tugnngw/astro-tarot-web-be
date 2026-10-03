package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.payment.PaymentInstructionResponse;
import com.exe.astratarot.domain.dto.wallet.UserWalletResponse;
import com.exe.astratarot.domain.dto.wallet.WalletTransactionResponse;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserWallet;
import com.exe.astratarot.domain.entity.WalletTransaction;
import com.exe.astratarot.domain.enums.WalletTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface WalletService {

    UserWallet getOrCreateWallet(UUID userId);

    UserWalletResponse getWalletResponse(UUID userId);

    WalletTransaction credit(User user, long amount, WalletTransactionType type, String referenceId, String description, String paymentMethod);

    WalletTransaction debit(User user, long amount, WalletTransactionType type, String referenceId, String description);

    PaymentInstructionResponse createTopupIntent(UUID userId, long amount);

    Page<WalletTransactionResponse> getTransactions(UUID userId, Pageable pageable);

    void payBookingWithWallet(UUID userId, UUID bookingId);
}
