package com.example.wallet;

import com.example.wallet.dto.CreateUserResponse;
import com.example.wallet.dto.OperationResult;
import com.example.wallet.dto.TopUpCommand;
import com.example.wallet.dto.TransferCommand;
import com.example.wallet.entity.ReconciliationRun;
import com.example.wallet.service.ReconciliationService;
import com.example.wallet.service.TransferFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "wallet.reconciliation.enabled=false")
class ReconciliationTest extends AbstractIntegrationTest {

    @Autowired
    private TestSupport testSupport;

    @Autowired
    private TransferFacade transferFacade;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Clean run produces status OK with zero discrepancies and total balance 0")
    void cleanRunProducesStatusOk() {
        CreateUserResponse u1 = testSupport.createFundedUser(50_000L);
        CreateUserResponse u2 = testSupport.createFundedUser(20_000L);
        transferFacade.transfer(new TransferCommand(u1.userId(), u2.userId(), 10_000L, testSupport.newKey()));

        Optional<ReconciliationRun> runOpt = reconciliationService.runOnce();
        assertThat(runOpt).isPresent();
        ReconciliationRun run = runOpt.get();

        assertThat(run.getStatus()).isEqualTo("OK");
        assertThat(run.getDiscrepancyCount()).isZero();
        assertThat(run.getTotalBalancePaise()).isZero();
        assertThat(run.getWalletsChecked()).isGreaterThanOrEqualTo(3); // system + at least 2 users
        assertThat(run.getTransactionsChecked()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("Tamper test A: balance tampering detected, then restored")
    void tamperTestABalanceTamperingDetectedAndRestored() {
        CreateUserResponse u = testSupport.createFundedUser(30_000L);

        // Tamper wallet balance
        jdbcTemplate.update("UPDATE wallets SET balance_paise = balance_paise + 1 WHERE id = ?", u.walletId());
        try {
            Optional<ReconciliationRun> runOpt = reconciliationService.runOnce();
            assertThat(runOpt).isPresent();
            ReconciliationRun run = runOpt.get();

            assertThat(run.getStatus()).isEqualTo("MISMATCH");
            assertThat(run.getDiscrepancyCount()).isGreaterThanOrEqualTo(2);

            boolean hasWalletMismatch = run.getDetails().stream().anyMatch(
                    d -> "WALLET_BALANCE_MISMATCH".equals(d.kind()) && u.walletId().toString().equals(d.ref()));
            boolean hasGlobalSumNonzero = run.getDetails().stream().anyMatch(
                    d -> "GLOBAL_SUM_NONZERO".equals(d.kind()));

            assertThat(hasWalletMismatch).isTrue();
            assertThat(hasGlobalSumNonzero).isTrue();
        } finally {
            // Restore wallet balance
            jdbcTemplate.update("UPDATE wallets SET balance_paise = balance_paise - 1 WHERE id = ?", u.walletId());
        }

        // Confirm next run is OK again
        Optional<ReconciliationRun> cleanOpt = reconciliationService.runOnce();
        assertThat(cleanOpt).isPresent();
        assertThat(cleanOpt.get().getStatus()).isEqualTo("OK");
        assertThat(cleanOpt.get().getDiscrepancyCount()).isZero();
    }

    @Test
    @DisplayName("Tamper test B: ledger amount tampering detected, then restored")
    void tamperTestBLedgerTamperingDetectedAndRestored() {
        CreateUserResponse u1 = testSupport.createFundedUser(30_000L);
        CreateUserResponse u2 = testSupport.createUser();

        OperationResult op = transferFacade.transfer(new TransferCommand(u1.userId(), u2.userId(), 5000L, testSupport.newKey()));
        UUID txId = op.body().transactionId();

        // Tamper one ledger entry
        jdbcTemplate.update("UPDATE ledger_entries SET amount_paise = amount_paise + 50 WHERE transaction_id = ? AND direction = 'CREDIT'", txId);
        try {
            Optional<ReconciliationRun> runOpt = reconciliationService.runOnce();
            assertThat(runOpt).isPresent();
            ReconciliationRun run = runOpt.get();

            assertThat(run.getStatus()).isEqualTo("MISMATCH");
            boolean hasLedgerImbalance = run.getDetails().stream().anyMatch(
                    d -> "LEDGER_IMBALANCE".equals(d.kind()) && txId.toString().equals(d.ref()));
            assertThat(hasLedgerImbalance).isTrue();
        } finally {
            // Restore ledger entry
            jdbcTemplate.update("UPDATE ledger_entries SET amount_paise = amount_paise - 50 WHERE transaction_id = ? AND direction = 'CREDIT'", txId);
        }

        // Confirm next run is OK again
        Optional<ReconciliationRun> cleanOpt = reconciliationService.runOnce();
        assertThat(cleanOpt).isPresent();
        assertThat(cleanOpt.get().getStatus()).isEqualTo("OK");
        assertThat(cleanOpt.get().getDiscrepancyCount()).isZero();
    }

    @Test
    @DisplayName("Consistency under load: snapshot isolation produces zero false mismatches during concurrent transfers")
    void consistencyUnderLoadProducesNoFalseMismatches() throws Exception {
        CreateUserResponse userA = testSupport.createFundedUser(500_000L);
        CreateUserResponse userB = testSupport.createFundedUser(500_000L);

        int transferCount = 100;
        List<Callable<OperationResult>> tasks = new ArrayList<>();
        for (int i = 0; i < transferCount; i++) {
            tasks.add(() -> transferFacade.transfer(
                    new TransferCommand(userA.userId(), userB.userId(), 100L, testSupport.newKey())));
        }

        // Run transfers in background and concurrently execute multiple reconciliation runs
        Thread transferThread = new Thread(() -> {
            try {
                testSupport.runConcurrently(tasks, 16);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        transferThread.start();

        List<ReconciliationRun> runsUnderLoad = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Optional<ReconciliationRun> r = reconciliationService.runOnce();
            r.ifPresent(runsUnderLoad::add);
            Thread.sleep(15);
        }

        transferThread.join();

        // Every run executed during load must be OK (no phantom discrepancies due to snapshot isolation)
        for (ReconciliationRun run : runsUnderLoad) {
            assertThat(run.getStatus()).isEqualTo("OK");
            assertThat(run.getDiscrepancyCount()).isZero();
            assertThat(run.getTotalBalancePaise()).isZero();
        }
    }
}

