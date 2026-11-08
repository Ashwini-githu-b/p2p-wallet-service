package com.example.wallet.service.strategy;

import com.example.wallet.dto.OperationResult;
import com.example.wallet.exception.InsufficientFundsException;
import com.example.wallet.repository.LedgerEntryRepository;
import com.example.wallet.repository.WalletRepository;
import com.example.wallet.repository.WalletTransactionRepository;
import com.example.wallet.service.IdempotencyChecker;
import com.example.wallet.service.MoneyMovement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "wallet.transfer.strategy", havingValue = "ATOMIC_UPDATE")
public class AtomicUpdateStrategy extends AbstractTransferStrategy {

    private final JdbcTemplate jdbcTemplate;
    private final WalletRepository walletRepository;

    public AtomicUpdateStrategy(
            IdempotencyChecker idempotencyChecker,
            WalletTransactionRepository txRepository,
            LedgerEntryRepository ledgerRepository,
            JdbcTemplate jdbcTemplate,
            WalletRepository walletRepository) {
        super(idempotencyChecker, txRepository, ledgerRepository);
        this.jdbcTemplate = jdbcTemplate;
        this.walletRepository = walletRepository;
    }

    @Override
    public String name() {
        return "ATOMIC_UPDATE";
    }

    @Override
    public OperationResult execute(MoneyMovement m) {
        Instant now = Instant.now();
        Timestamp nowTs = Timestamp.from(now);

        String debitSql = """
                UPDATE wallets SET balance_paise = balance_paise - ?, updated_at = ?
                 WHERE id = ? AND (is_system OR balance_paise >= ?)
                RETURNING balance_paise
                """;

        String creditSql = """
                UPDATE wallets SET balance_paise = balance_paise + ?, updated_at = ?
                 WHERE id = ?
                RETURNING balance_paise
                """;

        long fromBalanceAfter;
        long toBalanceAfter;

        boolean fromFirst = m.fromWalletId().compareTo(m.toWalletId()) < 0;

        if (fromFirst) {
            // Apply debit first (from < to)
            List<Long> debitRes = jdbcTemplate.query(
                    debitSql, (rs, i) -> rs.getLong(1),
                    m.amountPaise(), nowTs, m.fromWalletId(), m.amountPaise()
            );

            if (debitRes.isEmpty()) {
                // Debit failed (insufficient funds). Check if a concurrent request with same key committed.
                Optional<OperationResult> replay = replayIfKeyExists(m);
                if (replay.isPresent()) {
                    // Mark transaction as rollback-only so no partial changes commit
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    return replay.get();
                }
                throw new InsufficientFundsException(walletRepository.findUserIdById(m.fromWalletId()).orElse(null));
            }
            fromBalanceAfter = debitRes.get(0);

            // Apply credit second
            List<Long> creditRes = jdbcTemplate.query(
                    creditSql, (rs, i) -> rs.getLong(1),
                    m.amountPaise(), nowTs, m.toWalletId()
            );
            if (creditRes.isEmpty()) {
                throw new IllegalStateException("Recipient wallet not found: " + m.toWalletId());
            }
            toBalanceAfter = creditRes.get(0);

        } else {
            // Apply credit first (to < from)
            List<Long> creditRes = jdbcTemplate.query(
                    creditSql, (rs, i) -> rs.getLong(1),
                    m.amountPaise(), nowTs, m.toWalletId()
            );
            if (creditRes.isEmpty()) {
                throw new IllegalStateException("Recipient wallet not found: " + m.toWalletId());
            }
            toBalanceAfter = creditRes.get(0);

            // Apply debit second
            List<Long> debitRes = jdbcTemplate.query(
                    debitSql, (rs, i) -> rs.getLong(1),
                    m.amountPaise(), nowTs, m.fromWalletId(), m.amountPaise()
            );

            if (debitRes.isEmpty()) {
                // Debit failed. Check for an idempotency replay.
                Optional<OperationResult> replay = replayIfKeyExists(m);
                if (replay.isPresent()) {
                    /*
                     * Rollback-only is crucial here: when toWallet was updated first because toWalletId < fromWalletId,
                     * the credit has already executed in this transaction. If debit failed because the funds were
                     * already moved by an identical concurrent request that committed just now, we MUST roll back
                     * this transaction so the credit is undone, while safely returning the winner's replay result.
                     */
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    return replay.get();
                }
                throw new InsufficientFundsException(walletRepository.findUserIdById(m.fromWalletId()).orElse(null));
            }
            fromBalanceAfter = debitRes.get(0);
        }

        return record(m, fromBalanceAfter, toBalanceAfter, now);
    }
}

