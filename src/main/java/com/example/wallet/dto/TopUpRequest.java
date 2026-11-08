package com.example.wallet.dto;

import jakarta.validation.constraints.NotNull;

public record TopUpRequest(
    @NotNull Long amountPaise
) {
}

