package com.example.wallet.dto;

import com.example.wallet.entity.ReconciliationRun;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReconciliationResponse(
        UUID runId,
        Instant startedAt,
        Instant finishedAt,
        String status,
        int walletsChecked,
        int transactionsChecked,
        int discrepancyCount,
        long totalBalancePaise,
        List<Discrepancy> discrepancies
) {
    public static ReconciliationResponse from(ReconciliationRun run) {
        return new ReconciliationResponse(
                run.getId(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getStatus(),
                run.getWalletsChecked(),
                run.getTransactionsChecked(),
                run.getDiscrepancyCount(),
                run.getTotalBalancePaise(),
                run.getDetails()
        );
    }
}

