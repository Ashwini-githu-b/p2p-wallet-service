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
@Table(name = "wallet_transactions")
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private TransactionType type;

    @Column(name = "from_wallet_id", nullable = false)
    private UUID fromWalletId;

    @Column(name = "to_wallet_id", nullable = false)
    private UUID toWalletId;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WalletTransaction() {
    }

    public WalletTransaction(TransactionType type, UUID fromWalletId, UUID toWalletId, long amountPaise, String idempotencyKey, Instant createdAt) {
        this.type = type;
        this.fromWalletId = fromWalletId;
        this.toWalletId = toWalletId;
        this.amountPaise = amountPaise;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public UUID getFromWalletId() {
        return fromWalletId;
    }

    public UUID getToWalletId() {
        return toWalletId;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

