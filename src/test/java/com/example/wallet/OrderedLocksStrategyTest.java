package com.example.wallet;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "wallet.transfer.strategy=ORDERED_LOCKS",
        "wallet.reconciliation.enabled=false"
})
class OrderedLocksStrategyTest extends StrategyContractTest {

    @Override
    boolean conflictsPossible() {
        return false;
    }

    @Override
    String strategyName() {
        return "ORDERED_LOCKS";
    }
}

