package com.example.wallet.exception;

import org.springframework.http.HttpStatus;

public class MissingIdempotencyKeyException extends WalletException {

    public MissingIdempotencyKeyException() {
        super("MISSING_IDEMPOTENCY_KEY", HttpStatus.BAD_REQUEST, "Idempotency-Key header is missing or blank");
    }

    public MissingIdempotencyKeyException(String message) {
        super("MISSING_IDEMPOTENCY_KEY", HttpStatus.BAD_REQUEST, message);
    }
}

