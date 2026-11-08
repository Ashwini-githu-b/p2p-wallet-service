package com.example.wallet.service.strategy;

import com.example.wallet.dto.OperationResult;
import com.example.wallet.entity.LedgerEntry;
import com.example.wallet.entity.TransactionType;
import com.example.wallet.entity.WalletTransaction;
import com.example.wallet.repository.LedgerEntryRepository;
import com.example.wallet.repository.WalletTransactionRepository;
import com.example.wallet.service.IdempotencyChecker;
import com.example.wallet.service.MoneyMovement;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public abstract class AbstractTransferStrategy implements TransferStrategy {

    protected final IdempotencyChecker idempotencyChecker;
    protected final WalletTransactionRepository txRepository;
    protected final LedgerEntryRepository ledgerRepository;

    public AbstractTransferStrategy(
            IdempotencyChecker idempotencyChecker,
            WalletTransactionRepository txRepository,
            LedgerEntryRepository ledgerRepository) {
        this.idempotencyChecker = idempotencyChecker;
        this.txRepository = txRepository;
        this.ledgerRepository = ledgerRepository;
    }

    protected Optional<OperationResult> replayIfKeyExists(MoneyMovement m) {
        return idempotencyChecker.findReplay(m);
    }

    protected OperationResult record(MoneyMovement m, long fromBalanceAfter, long toBalanceAfter, Instant now) {
        WalletTransaction tx = txRepository.saveAndFlush(new WalletTransaction(
                m.type(), m.fromWalletId(), m.toWalletId(), m.amountPaise(), m.idempotencyKey(), now));

        // DEBIT row on fromWalletId
        ledgerRepository.save(LedgerEntry.debit(
                tx.getId(), m.fromWalletId(), m.amountPaise(), fromBalanceAfter, m.toWalletId(), now));

        // CREDIT row on toWalletId: counterparty_wallet_id is NULL for TOPUP to keep history identical to V1
        UUID creditCounterparty = m.type() == TransactionType.TOPUP ? null : m.fromWalletId();
        ledgerRepository.save(LedgerEntry.credit(
                tx.getId(), m.toWalletId(), m.amountPaise(), toBalanceAfter, creditCounterparty, now));

        return new OperationResult(idempotencyChecker.toResponse(tx), false);
    }
}

