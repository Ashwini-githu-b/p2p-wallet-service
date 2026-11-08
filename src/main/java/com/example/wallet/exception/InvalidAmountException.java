package com.example.wallet.exception;

import org.springframework.http.HttpStatus;

public class InvalidAmountException extends WalletException {

    public InvalidAmountException(String message) {
        super("INVALID_AMOUNT", HttpStatus.BAD_REQUEST, message);
    }

    public InvalidAmountException(long amountPaise) {
        super("INVALID_AMOUNT", HttpStatus.BAD_REQUEST, "Invalid amount: " + amountPaise);
    }
}

