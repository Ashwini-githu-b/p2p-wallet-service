package com.example.wallet;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "wallet.transfer.strategy=OPTIMISTIC",
        "wallet.reconciliation.enabled=false"
})
class OptimisticStrategyTest extends StrategyContractTest {

    @Override
    boolean conflictsPossible() {
        return true;
    }

    @Override
    String strategyName() {
        return "OPTIMISTIC";
    }
}

