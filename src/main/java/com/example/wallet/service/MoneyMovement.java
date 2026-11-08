package com.example.wallet.service;

import com.example.wallet.entity.TransactionType;

import java.util.UUID;

public record MoneyMovement(
        TransactionType type,
        UUID fromWalletId,
        UUID toWalletId,
        long amountPaise,
        String idempotencyKey
) {}

