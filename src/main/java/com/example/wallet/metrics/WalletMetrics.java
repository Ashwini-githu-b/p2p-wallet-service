package com.example.wallet.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class WalletMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger reconciliationDiscrepancies = new AtomicInteger(0);
    private final AtomicLong reconciliationLastRunTimestamp = new AtomicLong(0);

    public WalletMetrics(MeterRegistry registry) {
        this.registry = registry;

        registry.gauge("wallet.reconciliation.discrepancies", reconciliationDiscrepancies, AtomicInteger::get);
        registry.gauge("wallet.reconciliation.last.run.timestamp", reconciliationLastRunTimestamp, AtomicLong::get);
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void recordOperation(Timer.Sample sample, String type, String strategy, String outcome) {
        Counter.builder("wallet.operations")
                .tag("type", type)
                .tag("outcome", outcome)
                .register(registry)
                .increment();

        sample.stop(Timer.builder("wallet.operation.duration")
                .tag("type", type)
                .tag("strategy", strategy)
                .tag("outcome", outcome)
                .register(registry));
    }

    public void retry(String strategy) {
        Counter.builder("wallet.retries")
                .tag("strategy", strategy)
                .register(registry)
                .increment();
    }

    public double getRetryCount(String strategy) {
        Counter counter = registry.find("wallet.retries").tag("strategy", strategy).counter();
        return counter != null ? counter.count() : 0.0;
    }

    public void recordReconciliation(int discrepancyCount, long timestampEpochSeconds) {
        reconciliationDiscrepancies.set(discrepancyCount);
        reconciliationLastRunTimestamp.set(timestampEpochSeconds);
    }

    public MeterRegistry getRegistry() {
        return registry;
    }
}

