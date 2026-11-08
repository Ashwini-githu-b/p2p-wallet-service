package com.example.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "balance_paise", nullable = false)
    private long balancePaise;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "is_system", nullable = false)
    private boolean systemWallet = false;

    @Version
    @Column(name = "version", nullable = false)
    private long version = 0;

    protected Wallet() {
    }

    public Wallet(UUID userId, long balancePaise, Instant createdAt, Instant updatedAt) {
        this(userId, balancePaise, createdAt, updatedAt, false);
    }

    public Wallet(UUID userId, long balancePaise, Instant createdAt, Instant updatedAt, boolean systemWallet) {
        this.userId = userId;
        this.balancePaise = balancePaise;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.systemWallet = systemWallet;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public long getBalancePaise() {
        return balancePaise;
    }

    public void setBalancePaise(long balancePaise) {
        this.balancePaise = balancePaise;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isSystemWallet() {
        return systemWallet;
    }

    public void setSystemWallet(boolean systemWallet) {
        this.systemWallet = systemWallet;
    }

    public long getVersion() {
        return version;
    }
}

