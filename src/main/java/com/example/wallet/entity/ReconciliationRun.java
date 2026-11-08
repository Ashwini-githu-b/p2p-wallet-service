package com.example.wallet.entity;

import com.example.wallet.dto.Discrepancy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "wallets_checked", nullable = false)
    private int walletsChecked;

    @Column(name = "transactions_checked", nullable = false)
    private int transactionsChecked;

    @Column(name = "discrepancy_count", nullable = false)
    private int discrepancyCount;

    @Column(name = "total_balance_paise", nullable = false)
    private long totalBalancePaise;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false)
    private List<Discrepancy> details = new ArrayList<>();

    protected ReconciliationRun() {
    }

    public ReconciliationRun(Instant startedAt, Instant finishedAt, String status, int walletsChecked,
                             int transactionsChecked, int discrepancyCount, long totalBalancePaise,
                             List<Discrepancy> details) {
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.status = status;
        this.walletsChecked = walletsChecked;
        this.transactionsChecked = transactionsChecked;
        this.discrepancyCount = discrepancyCount;
        this.totalBalancePaise = totalBalancePaise;
        this.details = details != null ? details : new ArrayList<>();
    }

    public UUID getId() {
        return id;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getStatus() {
        return status;
    }

    public int getWalletsChecked() {
        return walletsChecked;
    }

    public int getTransactionsChecked() {
        return transactionsChecked;
    }

    public int getDiscrepancyCount() {
        return discrepancyCount;
    }

    public long getTotalBalancePaise() {
        return totalBalancePaise;
    }

    public List<Discrepancy> getDetails() {
        return details;
    }
}

