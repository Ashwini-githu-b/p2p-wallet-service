package com.example.wallet.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyKeyReusedException extends WalletException {

    public IdempotencyKeyReusedException() {
        super("IDEMPOTENCY_KEY_REUSED", HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key has already been used with different parameters");
    }

    public IdempotencyKeyReusedException(String message) {
        super("IDEMPOTENCY_KEY_REUSED", HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}

