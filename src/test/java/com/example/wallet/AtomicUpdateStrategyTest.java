package com.example.wallet;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "wallet.transfer.strategy=ATOMIC_UPDATE",
        "wallet.reconciliation.enabled=false"
})
class AtomicUpdateStrategyTest extends StrategyContractTest {

    @Override
    boolean conflictsPossible() {
        return false;
    }
}

