package com.example.wallet.service;

import com.example.wallet.dto.OperationResult;
import com.example.wallet.dto.TransactionResponse;
import com.example.wallet.entity.TransactionType;
import com.example.wallet.entity.WalletTransaction;
import com.example.wallet.exception.IdempotencyKeyReusedException;
import com.example.wallet.repository.WalletRepository;
import com.example.wallet.repository.WalletTransactionRepository;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class IdempotencyChecker {

    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletRepository walletRepository;

    public IdempotencyChecker(WalletTransactionRepository walletTransactionRepository,
                              WalletRepository walletRepository) {
        this.walletTransactionRepository = walletTransactionRepository;
        this.walletRepository = walletRepository;
    }

    public Optional<OperationResult> findReplay(MoneyMovement m) {
        Optional<WalletTransaction> existingOpt = walletTransactionRepository.findByIdempotencyKey(m.idempotencyKey());
        if (existingOpt.isEmpty()) {
            return Optional.empty();
        }

        WalletTransaction tx = existingOpt.get();
        boolean matches = tx.getType() == m.type()
                && Objects.equals(tx.getFromWalletId(), m.fromWalletId())
                && Objects.equals(tx.getToWalletId(), m.toWalletId())
                && tx.getAmountPaise() == m.amountPaise();

        if (matches) {
            return Optional.of(new OperationResult(toResponse(tx), true));
        }

        throw new IdempotencyKeyReusedException(m.idempotencyKey());
    }

    public TransactionResponse toResponse(WalletTransaction tx) {
        UUID fromUserId = tx.getType() == TransactionType.TOPUP
                ? null
                : walletRepository.findUserIdById(tx.getFromWalletId()).orElse(null);
        UUID toUserId = walletRepository.findUserIdById(tx.getToWalletId())
                .orElseThrow(() -> new IllegalStateException("Recipient wallet has no associated user"));
        return new TransactionResponse(
                tx.getId(),
                tx.getType(),
                fromUserId,
                toUserId,
                tx.getAmountPaise(),
                tx.getCreatedAt()
        );
    }
}

