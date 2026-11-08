package com.example.wallet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wallet.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final ReconciliationService reconciliationService;

    public ReconciliationScheduler(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(
            initialDelayString = "${wallet.reconciliation.initial-delay-ms:30000}",
            fixedDelayString = "${wallet.reconciliation.interval-ms:300000}"
    )
    public void schedule() {
        try {
            reconciliationService.runOnce();
        } catch (Exception e) {
            log.error("Unhandled error during scheduled reconciliation execution", e);
        }
    }
}

