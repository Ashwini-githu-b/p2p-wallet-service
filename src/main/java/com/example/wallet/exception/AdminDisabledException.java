package com.example.wallet.exception;

import org.springframework.http.HttpStatus;

public class AdminDisabledException extends WalletException {
    public AdminDisabledException() {
        super("ADMIN_DISABLED", HttpStatus.FORBIDDEN, "Admin operations are disabled");
    }
}

