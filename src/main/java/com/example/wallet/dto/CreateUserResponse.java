package com.example.wallet.dto;

import java.util.UUID;

public record CreateUserResponse(
    UUID userId,
    String name,
    String email,
    UUID walletId,
    long balancePaise
) {
}

