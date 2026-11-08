package com.example.wallet.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class UserNotFoundException extends WalletException {

    public UserNotFoundException(UUID userId) {
        super("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "User not found: " + userId);
    }

    public UserNotFoundException(String message) {
        super("USER_NOT_FOUND", HttpStatus.NOT_FOUND, message);
    }
}

