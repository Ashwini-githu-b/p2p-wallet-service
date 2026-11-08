package com.example.wallet;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MigrationBackfillTest {

    private static final String SYSTEM_WALLET_ID = "00000000-0000-0000-0000-000000000002";

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    @DisplayName("V2 migration backfills historical top-ups into double-entry ledger and balances system account")
    void migrationBackfillsHistoricalTopUps() throws Exception {
        // 1. Migrate up to target version 1 (V1 schema only)
        Flyway flywayV1 = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("1")
                .load();
        flywayV1.migrate();

        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();
        UUID wallet1Id = UUID.randomUUID();
        UUID wallet2Id = UUID.randomUUID();

        UUID tx1Id = UUID.randomUUID();
        UUID tx2Id = UUID.randomUUID();
        UUID tx3Id = UUID.randomUUID();

        long tx1Amount = 5000L;
        long tx2Amount = 3000L;
        long tx3Amount = 2000L;
        long totalTopUp = tx1Amount + tx2Amount + tx3Amount;

        Instant now = Instant.now();

        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

            // Insert 2 users
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (id, name, email, created_at) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, user1Id);
                ps.setString(2, "Backfill User 1");
                ps.setString(3, "backfill1@example.com");
                ps.setTimestamp(4, Timestamp.from(now));
                ps.executeUpdate();

                ps.setObject(1, user2Id);
                ps.setString(2, "Backfill User 2");
                ps.setString(3, "backfill2@example.com");
                ps.setTimestamp(4, Timestamp.from(now));
                ps.executeUpdate();
            }

            // Insert 2 wallets with initial balances reflecting topups
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO wallets (id, user_id, balance_paise, created_at, updated_at) VALUES (?, ?, ?, ?, ?)")) {
                ps.setObject(1, wallet1Id);
                ps.setObject(2, user1Id);
                ps.setLong(3, tx1Amount + tx2Amount);
                ps.setTimestamp(4, Timestamp.from(now));
                ps.setTimestamp(5, Timestamp.from(now));
                ps.executeUpdate();

                ps.setObject(1, wallet2Id);
                ps.setObject(2, user2Id);
                ps.setLong(3, tx3Amount);
                ps.setTimestamp(4, Timestamp.from(now));
                ps.setTimestamp(5, Timestamp.from(now));
                ps.executeUpdate();
            }

            // Insert 3 TOPUP transactions with from_wallet_id = NULL
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO wallet_transactions (id, type, from_wallet_id, to_wallet_id, amount_paise, idempotency_key, created_at) VALUES (?, ?, NULL, ?, ?, ?, ?)")) {
                ps.setObject(1, tx1Id);
                ps.setString(2, "TOPUP");
                ps.setObject(3, wallet1Id);
                ps.setLong(4, tx1Amount);
                ps.setString(5, "topup-key-1");
                ps.setTimestamp(6, Timestamp.from(now.minusSeconds(30)));
                ps.executeUpdate();

                ps.setObject(1, tx2Id);
                ps.setString(2, "TOPUP");
                ps.setObject(3, wallet1Id);
                ps.setLong(4, tx2Amount);
                ps.setString(5, "topup-key-2");
                ps.setTimestamp(6, Timestamp.from(now.minusSeconds(20)));
                ps.executeUpdate();

                ps.setObject(1, tx3Id);
                ps.setString(2, "TOPUP");
                ps.setObject(3, wallet2Id);
                ps.setLong(4, tx3Amount);
                ps.setString(5, "topup-key-3");
                ps.setTimestamp(6, Timestamp.from(now.minusSeconds(10)));
                ps.executeUpdate();
            }

            // Insert single CREDIT ledger entries for V1 topups
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ledger_entries (id, transaction_id, wallet_id, direction, amount_paise, balance_after_paise, counterparty_wallet_id, created_at) VALUES (?, ?, ?, 'CREDIT', ?, ?, NULL, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tx1Id);
                ps.setObject(3, wallet1Id);
                ps.setLong(4, tx1Amount);
                ps.setLong(5, tx1Amount);
                ps.setTimestamp(6, Timestamp.from(now.minusSeconds(30)));
                ps.executeUpdate();

                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tx2Id);
                ps.setObject(3, wallet1Id);
                ps.setLong(4, tx2Amount);
                ps.setLong(5, tx1Amount + tx2Amount);
                ps.setTimestamp(6, Timestamp.from(now.minusSeconds(20)));
                ps.executeUpdate();

                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tx3Id);
                ps.setObject(3, wallet2Id);
                ps.setLong(4, tx3Amount);
                ps.setLong(5, tx3Amount);
                ps.setTimestamp(6, Timestamp.from(now.minusSeconds(10)));
                ps.executeUpdate();
            }
        }

        // 2. Migrate to latest (running V2)
        Flyway flywayLatest = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load();
        flywayLatest.migrate();

        // 3. Verify assertions
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            // System wallet balance = -(sum of topup amounts)
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT balance_paise FROM wallets WHERE id = '" + SYSTEM_WALLET_ID + "'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("balance_paise")).isEqualTo(-totalTopUp);
            }

            // Every topup transaction now has from_wallet_id = system wallet
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT count(*) FROM wallet_transactions WHERE type = 'TOPUP' AND from_wallet_id = '" + SYSTEM_WALLET_ID + "'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isEqualTo(3L);
            }

            // Every topup transaction has exactly 1 DEBIT + 1 CREDIT ledger row
            for (UUID txId : new UUID[]{tx1Id, tx2Id, tx3Id}) {
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT direction, count(*) FROM ledger_entries WHERE transaction_id = '" + txId + "' GROUP BY direction")) {
                    int rows = 0;
                    while (rs.next()) {
                        rows++;
                        assertThat(rs.getLong(2)).isEqualTo(1L);
                    }
                    assertThat(rows).isEqualTo(2);
                }
            }

            // SUM(balance_paise) over ALL wallets = 0
            try (ResultSet rs = stmt.executeQuery("SELECT COALESCE(SUM(balance_paise), 0) FROM wallets")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isEqualTo(0L);
            }

            // Per-wallet balance equals the ledger sum for every wallet (including system)
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT w.id, w.balance_paise, " +
                    "COALESCE(SUM(CASE le.direction WHEN 'CREDIT' THEN le.amount_paise ELSE -le.amount_paise END), 0) AS ledger_sum " +
                    "FROM wallets w LEFT JOIN ledger_entries le ON le.wallet_id = w.id " +
                    "GROUP BY w.id, w.balance_paise")) {
                while (rs.next()) {
                    long balance = rs.getLong("balance_paise");
                    long ledgerSum = rs.getLong("ledger_sum");
                    assertThat(balance).isEqualTo(ledgerSum);
                }
            }
        }
    }
}

