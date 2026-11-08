package com.example.wallet.dto;

import java.util.UUID;

public record TransferCommand(
    UUID fromUserId,
    UUID toUserId,
    long amountPaise,
    String idempotencyKey
) {
}

