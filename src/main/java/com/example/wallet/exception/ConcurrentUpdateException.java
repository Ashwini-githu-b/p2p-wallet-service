package com.example.wallet.exception;

import org.springframework.http.HttpStatus;

public class ConcurrentUpdateException extends WalletException {
    public ConcurrentUpdateException() {
        super("CONCURRENT_UPDATE", HttpStatus.CONFLICT, "The wallet was updated concurrently. Please retry.");
    }
}

