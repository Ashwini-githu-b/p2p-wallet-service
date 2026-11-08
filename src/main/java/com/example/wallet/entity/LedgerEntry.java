package com.example.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 8)
    private EntryDirection direction;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "balance_after_paise", nullable = false)
    private long balanceAfterPaise;

    @Column(name = "counterparty_wallet_id")
    private UUID counterpartyWalletId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(UUID transactionId, UUID walletId, EntryDirection direction, long amountPaise, long balanceAfterPaise, UUID counterpartyWalletId, Instant createdAt) {
        this.transactionId = transactionId;
        this.walletId = walletId;
        this.direction = direction;
        this.amountPaise = amountPaise;
        this.balanceAfterPaise = balanceAfterPaise;
        this.counterpartyWalletId = counterpartyWalletId;
        this.createdAt = createdAt;
    }

    public static LedgerEntry debit(UUID txId, UUID walletId, long amount, long balanceAfter, UUID counterpartyWalletId, Instant now) {
        return new LedgerEntry(txId, walletId, EntryDirection.DEBIT, amount, balanceAfter, counterpartyWalletId, now);
    }

    public static LedgerEntry credit(UUID txId, UUID walletId, long amount, long balanceAfter, UUID counterpartyWalletId, Instant now) {
        return new LedgerEntry(txId, walletId, EntryDirection.CREDIT, amount, balanceAfter, counterpartyWalletId, now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public EntryDirection getDirection() {
        return direction;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public long getBalanceAfterPaise() {
        return balanceAfterPaise;
    }

    public UUID getCounterpartyWalletId() {
        return counterpartyWalletId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

