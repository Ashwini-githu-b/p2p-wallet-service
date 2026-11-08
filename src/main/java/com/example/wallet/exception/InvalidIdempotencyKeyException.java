package com.example.wallet.exception;

import org.springframework.http.HttpStatus;

public class InvalidIdempotencyKeyException extends WalletException {

    public InvalidIdempotencyKeyException() {
        super("INVALID_IDEMPOTENCY_KEY", HttpStatus.BAD_REQUEST, "Idempotency-Key header is invalid");
    }

    public InvalidIdempotencyKeyException(String message) {
        super("INVALID_IDEMPOTENCY_KEY", HttpStatus.BAD_REQUEST, message);
    }
}

