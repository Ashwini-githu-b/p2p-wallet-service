package com.example.wallet.dto;

import com.example.wallet.entity.TransactionType;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
    UUID transactionId,
    TransactionType type,
    UUID fromUserId,
    UUID toUserId,
    long amountPaise,
    Instant createdAt
) {
}

