package com.example.wallet.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends WalletException {

    public EmailAlreadyExistsException() {
        super("EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT, "Email already exists");
    }

    public EmailAlreadyExistsException(String email) {
        super("EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT, "Email already exists: " + email);
    }
}

