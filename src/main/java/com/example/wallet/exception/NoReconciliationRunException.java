package com.example.wallet.exception;

import org.springframework.http.HttpStatus;

public class NoReconciliationRunException extends WalletException {
    public NoReconciliationRunException() {
        super("NO_RECONCILIATION_RUN", HttpStatus.NOT_FOUND, "No reconciliation run found");
    }
}

