package com.example.wallet.service;

import com.example.wallet.dto.AuditResult;
import com.example.wallet.dto.Discrepancy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class LedgerAuditor {

    private final JdbcTemplate jdbcTemplate;

    public LedgerAuditor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Executes the ledger audit under REPEATABLE READ snapshot isolation.
     * All queries must observe the exact same consistent database snapshot while
     * concurrent transfers continue committing, preventing false mismatch alerts.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
    public Optional<AuditResult> audit() {
        // Attempt transaction-level advisory lock (released automatically on commit/rollback).
        // This first statement also binds and fixes the transaction snapshot in REPEATABLE READ.
        Boolean acquired = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(7001)", Boolean.class);
        if (Boolean.FALSE.equals(acquired)) {
            return Optional.empty();
        }

        List<Discrepancy> discrepancies = new ArrayList<>();

        // (1) Wallet balance vs ledger sum
        String balanceMismatchQuery = """
                SELECT w.id, w.balance_paise AS actual,
                       COALESCE(SUM(CASE le.direction WHEN 'CREDIT' THEN le.amount_paise
                                                      ELSE -le.amount_paise END), 0) AS expected
                  FROM wallets w LEFT JOIN ledger_entries le ON le.wallet_id = w.id
                 GROUP BY w.id, w.balance_paise
                HAVING w.balance_paise <> COALESCE(SUM(CASE le.direction WHEN 'CREDIT' THEN le.amount_paise
                                                                         ELSE -le.amount_paise END), 0)
                """;

        jdbcTemplate.query(balanceMismatchQuery, rs -> {
            UUID walletId = (UUID) rs.getObject("id");
            long actual = rs.getLong("actual");
            long expected = rs.getLong("expected");
            discrepancies.add(new Discrepancy("WALLET_BALANCE_MISMATCH", walletId.toString(), expected, actual));
        });

        // (2) Transaction double-entry ledger balance (1 DEBIT + 1 CREDIT matching amount)
        String txMismatchQuery = """
                SELECT t.id, t.amount_paise,
                       COUNT(*) FILTER (WHERE le.direction = 'DEBIT')  AS debits,
                       COUNT(*) FILTER (WHERE le.direction = 'CREDIT') AS credits,
                       COALESCE(SUM(le.amount_paise) FILTER (WHERE le.direction = 'DEBIT'),  0) AS debit_total,
                       COALESCE(SUM(le.amount_paise) FILTER (WHERE le.direction = 'CREDIT'), 0) AS credit_total
                  FROM wallet_transactions t LEFT JOIN ledger_entries le ON le.transaction_id = t.id
                 GROUP BY t.id, t.amount_paise
                HAVING COUNT(*) FILTER (WHERE le.direction = 'DEBIT')  <> 1
                    OR COUNT(*) FILTER (WHERE le.direction = 'CREDIT') <> 1
                    OR COALESCE(SUM(le.amount_paise) FILTER (WHERE le.direction = 'DEBIT'),  0) <> t.amount_paise
                    OR COALESCE(SUM(le.amount_paise) FILTER (WHERE le.direction = 'CREDIT'), 0) <> t.amount_paise
                """;

        jdbcTemplate.query(txMismatchQuery, rs -> {
            UUID txId = (UUID) rs.getObject("id");
            long expected = rs.getLong("amount_paise");
            long debitTotal = rs.getLong("debit_total");
            long creditTotal = rs.getLong("credit_total");
            long actual = (debitTotal != expected) ? debitTotal : creditTotal;
            discrepancies.add(new Discrepancy("LEDGER_IMBALANCE", txId.toString(), expected, actual));
        });

        // (3) Global conservation of money across all wallets including system
        Long totalBalancePaise = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(balance_paise), 0) FROM wallets", Long.class);
        if (totalBalancePaise == null) {
            totalBalancePaise = 0L;
        }

        if (totalBalancePaise != 0L) {
            discrepancies.add(new Discrepancy("GLOBAL_SUM_NONZERO", "all", 0L, totalBalancePaise));
        }

        Integer walletsChecked = jdbcTemplate.queryForObject("SELECT count(*) FROM wallets", Integer.class);
        Integer transactionsChecked = jdbcTemplate.queryForObject("SELECT count(*) FROM wallet_transactions", Integer.class);

        return Optional.of(new AuditResult(
                walletsChecked != null ? walletsChecked : 0,
                transactionsChecked != null ? transactionsChecked : 0,
                totalBalancePaise,
                discrepancies
        ));
    }
}

