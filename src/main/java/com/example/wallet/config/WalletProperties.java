package com.example.wallet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "wallet")
public record WalletProperties(
        @DefaultValue("10000000") long maxAmountPaise,
        @DefaultValue("") String adminToken,
        @DefaultValue Transfer transfer,
        @DefaultValue Reconciliation reconciliation
) {
    public record Transfer(
            @DefaultValue("ORDERED_LOCKS") String strategy,
            @DefaultValue Retry retry
    ) {}

    public record Retry(
            @DefaultValue("10") int maxRetries,
            @DefaultValue("2") long baseBackoffMs,
            @DefaultValue("50") long maxBackoffMs
    ) {}

    public record Reconciliation(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("300000") long intervalMs,
            @DefaultValue("30000") long initialDelayMs
    ) {}
}
