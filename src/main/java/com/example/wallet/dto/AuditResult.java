package com.example.wallet.dto;

import java.util.List;

public record AuditResult(
        int walletsChecked,
        int transactionsChecked,
        long totalBalancePaise,
        List<Discrepancy> discrepancies
) {}

