package com.example.wallet.exception;

import org.springframework.http.HttpStatus;

public class SelfTransferException extends WalletException {

    public SelfTransferException() {
        super("SELF_TRANSFER", HttpStatus.BAD_REQUEST, "Self-transfer is not allowed");
    }

    public SelfTransferException(String message) {
        super("SELF_TRANSFER", HttpStatus.BAD_REQUEST, message);
    }
}

