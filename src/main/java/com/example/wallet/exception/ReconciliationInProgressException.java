package com.example.wallet.exception;

import org.springframework.http.HttpStatus;

public class ReconciliationInProgressException extends WalletException {
    public ReconciliationInProgressException() {
        super("RECONCILIATION_IN_PROGRESS", HttpStatus.CONFLICT, "A reconciliation run is already in progress");
    }
}

