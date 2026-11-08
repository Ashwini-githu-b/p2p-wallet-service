package com.example.wallet.dto;

import com.example.wallet.entity.EntryDirection;
import com.example.wallet.entity.TransactionType;
import java.time.Instant;
import java.util.UUID;

public record HistoryItem(
    UUID transactionId,
    TransactionType type,
    EntryDirection direction,
    long amountPaise,
    long balanceAfterPaise,
    UUID counterpartyUserId,
    Instant createdAt
) {
}

