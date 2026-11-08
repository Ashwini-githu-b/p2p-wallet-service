package com.example.wallet.exception;

import org.springframework.http.HttpStatus;

public abstract class WalletException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    protected WalletException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

