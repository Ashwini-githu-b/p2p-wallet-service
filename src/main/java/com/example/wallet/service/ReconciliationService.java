package com.example.wallet.service;

import com.example.wallet.dto.AuditResult;
import com.example.wallet.dto.Discrepancy;
import com.example.wallet.entity.ReconciliationRun;
import com.example.wallet.repository.ReconciliationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final LedgerAuditor ledgerAuditor;
    private final ReconciliationRunRepository runRepository;

    public ReconciliationService(LedgerAuditor ledgerAuditor, ReconciliationRunRepository runRepository) {
        this.ledgerAuditor = ledgerAuditor;
        this.runRepository = runRepository;
    }

    public Optional<ReconciliationRun> runOnce() {
        Instant startedAt = Instant.now();
        Optional<AuditResult> auditOpt = ledgerAuditor.audit();
        if (auditOpt.isEmpty()) {
            log.warn("Reconciliation skipped: advisory lock 7001 is currently held by another worker.");
            return Optional.empty();
        }

        AuditResult audit = auditOpt.get();
        Instant finishedAt = Instant.now();

        int discrepancyCount = audit.discrepancies().size();
        String status = discrepancyCount == 0 ? "OK" : "MISMATCH";

        List<Discrepancy> cappedDetails = audit.discrepancies().stream()
                .limit(100)
                .toList();

        ReconciliationRun run = new ReconciliationRun(
                startedAt,
                finishedAt,
                status,
                audit.walletsChecked(),
                audit.transactionsChecked(),
                discrepancyCount,
                audit.totalBalancePaise(),
                cappedDetails
        );

        run = runRepository.save(run);

        if ("OK".equals(status)) {
            log.info("Reconciliation run OK: checked {} wallets, {} transactions. Zero discrepancies.",
                    audit.walletsChecked(), audit.transactionsChecked());
        } else {
            log.error("Reconciliation run MISMATCH: {} discrepancies found (checked {} wallets, {} transactions). Total balance: {}",
                    discrepancyCount, audit.walletsChecked(), audit.transactionsChecked(), audit.totalBalancePaise());
        }

        return Optional.of(run);
    }

    public Optional<ReconciliationRun> getLatestRun() {
        return runRepository.findFirstByOrderByStartedAtDesc();
    }
}

