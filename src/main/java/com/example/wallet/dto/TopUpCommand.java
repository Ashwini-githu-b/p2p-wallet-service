package com.example.wallet.dto;

import java.util.UUID;

public record TopUpCommand(
    UUID userId,
    long amountPaise,
    String idempotencyKey
) {
}

