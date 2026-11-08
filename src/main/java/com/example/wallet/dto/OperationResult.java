package com.example.wallet.dto;

public record OperationResult(
    TransactionResponse body,
    boolean replayed
) {
}

