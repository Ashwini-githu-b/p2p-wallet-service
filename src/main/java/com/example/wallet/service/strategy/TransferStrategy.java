package com.example.wallet.service.strategy;

import com.example.wallet.dto.OperationResult;
import com.example.wallet.service.MoneyMovement;

public interface TransferStrategy {
    OperationResult execute(MoneyMovement m);
}

