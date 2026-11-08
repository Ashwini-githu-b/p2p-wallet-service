package com.example.wallet.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends WalletException {
    public UnauthorizedException() {
        super("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "Invalid or missing admin token");
    }
}

