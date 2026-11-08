package com.example.wallet.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class InsufficientFundsException extends WalletException {

    public InsufficientFundsException() {
        super("INSUFFICIENT_FUNDS", HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds");
    }

    public InsufficientFundsException(UUID userId) {
        super("INSUFFICIENT_FUNDS", HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds for user: " + userId);
    }

    public InsufficientFundsException(String message) {
        super("INSUFFICIENT_FUNDS", HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}

