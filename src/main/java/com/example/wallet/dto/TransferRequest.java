package com.example.wallet.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TransferRequest(
    @NotNull UUID fromUserId,
    @NotNull UUID toUserId,
    @NotNull Long amountPaise
) {
}

