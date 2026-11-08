package com.example.wallet;

import com.example.wallet.config.WalletProperties;
import com.example.wallet.dto.CreateUserResponse;
import com.example.wallet.dto.OperationResult;
import com.example.wallet.dto.TopUpCommand;
import com.example.wallet.dto.TransferCommand;
import com.example.wallet.exception.ConcurrentUpdateException;
import com.example.wallet.exception.IdempotencyKeyReusedException;
import com.example.wallet.exception.InsufficientFundsException;
import com.example.wallet.exception.InvalidAmountException;
import com.example.wallet.exception.SelfTransferException;
import com.example.wallet.exception.UserNotFoundException;
import com.example.wallet.repository.WalletTransactionRepository;
import com.example.wallet.service.TransferFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class StrategyContractTest extends AbstractIntegrationTest {

    @Autowired
    protected TestSupport testSupport;

    @Autowired
    protected TransferFacade transferFacade;

    @Autowired
    protected WalletTransactionRepository txRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected WalletProperties walletProperties;

    @Autowired
    protected com.example.wallet.metrics.WalletMetrics walletMetrics;

    abstract boolean conflictsPossible();
    abstract String strategyName();

    // ==========================================
    // Concurrency & Invariants
    // ==========================================

    @Test
    @DisplayName("Random concurrent transfers conserve money and maintain ledger integrity")
    void randomConcurrentTransfersConserveMoney() throws Exception {
        int userCount = 10;
        long initialFundsPerUser = 1_000_000L;
        long expectedTotalFunds = userCount * initialFundsPerUser;

        List<CreateUserResponse> users = new ArrayList<>();
        List<UUID> userIds = new ArrayList<>();
        List<UUID> walletIds = new ArrayList<>();

        for (int i = 0; i < userCount; i++) {
            CreateUserResponse user = testSupport.createFundedUser(initialFundsPerUser);
            users.add(user);
            userIds.add(user.userId());
            walletIds.add(user.walletId());
        }

        long initialRetries = transferFacade.getRetryCount();
        double initialMetricRetries = walletMetrics.getRetryCount(strategyName());

        int totalTasks = 200;
        int threads = 32;
        Random random = new Random(42);
        List<Callable<OperationResult>> tasks = new ArrayList<>();

        for (int i = 0; i < totalTasks; i++) {
            int senderIdx = random.nextInt(userCount);
            int receiverIdx = random.nextInt(userCount);
            while (receiverIdx == senderIdx) {
                receiverIdx = random.nextInt(userCount);
            }
            UUID fromUserId = userIds.get(senderIdx);
            UUID toUserId = userIds.get(receiverIdx);
            long amount = 1 + random.nextInt(50_000);
            String idempotencyKey = testSupport.newKey();

            tasks.add(() -> transferFacade.transfer(new TransferCommand(fromUserId, toUserId, amount, idempotencyKey)));
        }

        List<TestSupport.TaskOutcome<OperationResult>> outcomes = testSupport.runConcurrently(tasks, threads);

        long successfulTransfers = 0;
        for (TestSupport.TaskOutcome<OperationResult> outcome : outcomes) {
            if (outcome.isSuccess()) {
                successfulTransfers++;
            } else {
                if (conflictsPossible()) {
                    assertThat(outcome.exception())
                            .isInstanceOfAny(InsufficientFundsException.class, ConcurrentUpdateException.class);
                } else {
                    assertThat(outcome.exception())
                            .as("Every failure must be InsufficientFundsException, proving no deadlocks or unhandled conflicts")
                            .isInstanceOf(InsufficientFundsException.class);
                }
            }
        }

        if (!conflictsPossible()) {
            assertThat(transferFacade.getRetryCount() - initialRetries)
                    .as("Retry count must stay at 0 for non-optimistic strategies")
                    .isZero();
            assertThat(walletMetrics.getRetryCount(strategyName()) - initialMetricRetries)
                    .as("wallet_retries_total delta must be exactly 0 for non-optimistic strategies")
                    .isZero();
        }

        assertThat(successfulTransfers).isGreaterThan(0);
        assertThat(testSupport.sumBalances(userIds)).isEqualTo(expectedTotalFunds);

        for (int i = 0; i < userCount; i++) {
            UUID userId = userIds.get(i);
            UUID walletId = walletIds.get(i);
            long bal = testSupport.balance(userId);

            assertThat(bal).isGreaterThanOrEqualTo(0L);
            assertThat(bal).isEqualTo(testSupport.ledgerSum(walletId));
        }

        long totalLedgerEntries = testSupport.countLedgerEntries(walletIds);
        assertThat(totalLedgerEntries).isEqualTo(userCount + (2 * successfulTransfers));

        // Global conservation check: SUM over all wallets including system = 0
        Long globalSum = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(balance_paise), 0) FROM wallets", Long.class);
        assertThat(globalSum).isEqualTo(0L);
    }

    @Test
    @DisplayName("Bidirectional transfers between two users execute safely without deadlocks")
    void bidirectionalTransfersDoNotDeadlock() throws Exception {
        long initialFunds = 1_000_000L;
        CreateUserResponse userA = testSupport.createFundedUser(initialFunds);
        CreateUserResponse userB = testSupport.createFundedUser(initialFunds);

        long initialRetries = transferFacade.getRetryCount();
        double initialMetricRetries = walletMetrics.getRetryCount(strategyName());

        List<Callable<OperationResult>> tasks = new ArrayList<>();
        long transferAmount = 100L;

        List<String> directions = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String keyA = testSupport.newKey();
            tasks.add(() -> transferFacade.transfer(new TransferCommand(userA.userId(), userB.userId(), transferAmount, keyA)));
            directions.add("A->B");

            String keyB = testSupport.newKey();
            tasks.add(() -> transferFacade.transfer(new TransferCommand(userB.userId(), userA.userId(), transferAmount, keyB)));
            directions.add("B->A");
        }

        List<TestSupport.TaskOutcome<OperationResult>> outcomes = testSupport.runConcurrently(tasks, 32);

        if (!conflictsPossible()) {
            for (TestSupport.TaskOutcome<OperationResult> outcome : outcomes) {
                assertThat(outcome.exception()).isNull();
                assertThat(outcome.result()).isNotNull();
            }
            assertThat(transferFacade.getRetryCount() - initialRetries).isZero();
            assertThat(walletMetrics.getRetryCount(strategyName()) - initialMetricRetries).isZero();
            assertThat(testSupport.balance(userA.userId())).isEqualTo(initialFunds);
            assertThat(testSupport.balance(userB.userId())).isEqualTo(initialFunds);
        } else {
            long aToBSuccesses = 0;
            long bToASuccesses = 0;
            for (int i = 0; i < outcomes.size(); i++) {
                TestSupport.TaskOutcome<OperationResult> outcome = outcomes.get(i);
                String dir = directions.get(i);
                if (outcome.isSuccess()) {
                    if ("A->B".equals(dir)) aToBSuccesses++;
                    else bToASuccesses++;
                } else {
                    assertThat(outcome.exception()).isInstanceOf(ConcurrentUpdateException.class);
                }
            }

            long expectedA = initialFunds - (transferAmount * aToBSuccesses) + (transferAmount * bToASuccesses);
            long expectedB = initialFunds + (transferAmount * aToBSuccesses) - (transferAmount * bToASuccesses);
            assertThat(testSupport.balance(userA.userId())).isEqualTo(expectedA);
            assertThat(testSupport.balance(userB.userId())).isEqualTo(expectedB);
        }
    }

    @Test
    @DisplayName("Overdraft race never produces a negative balance")
    void overdraftRaceNeverGoesNegative() throws Exception {
        CreateUserResponse sender = testSupport.createFundedUser(1_000L);
        int receiverCount = 20;
        List<CreateUserResponse> receivers = new ArrayList<>();
        List<UUID> receiverIds = new ArrayList<>();

        for (int i = 0; i < receiverCount; i++) {
            CreateUserResponse receiver = testSupport.createUser();
            receivers.add(receiver);
            receiverIds.add(receiver.userId());
        }

        long initialRetries = transferFacade.getRetryCount();
        double initialMetricRetries = walletMetrics.getRetryCount(strategyName());

        List<Callable<OperationResult>> tasks = new ArrayList<>();
        for (int i = 0; i < receiverCount; i++) {
            UUID receiverId = receiverIds.get(i);
            String key = testSupport.newKey();
            tasks.add(() -> transferFacade.transfer(new TransferCommand(sender.userId(), receiverId, 100L, key)));
        }

        List<TestSupport.TaskOutcome<OperationResult>> outcomes = testSupport.runConcurrently(tasks, receiverCount);

        long successes = outcomes.stream().filter(TestSupport.TaskOutcome::isSuccess).count();

        if (!conflictsPossible()) {
            long failures = outcomes.stream().filter(o -> !o.isSuccess()).count();
            assertThat(successes).isEqualTo(10L);
            assertThat(failures).isEqualTo(10L);

            for (TestSupport.TaskOutcome<OperationResult> outcome : outcomes) {
                if (!outcome.isSuccess()) {
                    assertThat(outcome.exception()).isInstanceOf(InsufficientFundsException.class);
                }
            }
            assertThat(transferFacade.getRetryCount() - initialRetries).isZero();
            assertThat(walletMetrics.getRetryCount(strategyName()) - initialMetricRetries).isZero();
            assertThat(testSupport.balance(sender.userId())).isEqualTo(0L);
            assertThat(testSupport.sumBalances(receiverIds)).isEqualTo(1_000L);
        } else {
            assertThat(successes).isLessThanOrEqualTo(10L);
            for (TestSupport.TaskOutcome<OperationResult> outcome : outcomes) {
                if (!outcome.isSuccess()) {
                    assertThat(outcome.exception())
                            .isInstanceOfAny(InsufficientFundsException.class, ConcurrentUpdateException.class);
                }
            }
            long senderBalance = testSupport.balance(sender.userId());
            assertThat(senderBalance).isGreaterThanOrEqualTo(0L);
            assertThat(senderBalance).isEqualTo(1000L - (100L * successes));
            assertThat(testSupport.sumBalances(receiverIds)).isEqualTo(100L * successes);
        }
    }

    // ==========================================
    // Idempotency Tests
    // ==========================================

    @Test
    @DisplayName("Concurrent replays with the same key move money exactly once")
    void sameKeyConcurrentReplayMovesMoneyOnce() throws Exception {
        CreateUserResponse userA = testSupport.createFundedUser(100_000L);
        CreateUserResponse userB = testSupport.createUser();
        String key = testSupport.newKey();

        long initialRetries = transferFacade.getRetryCount();
        double initialMetricRetries = walletMetrics.getRetryCount(strategyName());

        List<Callable<OperationResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> transferFacade.transfer(new TransferCommand(userA.userId(), userB.userId(), 500L, key)));
        }

        List<TestSupport.TaskOutcome<OperationResult>> outcomes = testSupport.runConcurrently(tasks, 20);

        assertThat(outcomes).allMatch(TestSupport.TaskOutcome::isSuccess);

        UUID expectedTxId = outcomes.getFirst().result().body().transactionId();
        for (TestSupport.TaskOutcome<OperationResult> outcome : outcomes) {
            assertThat(outcome.result().body().transactionId()).isEqualTo(expectedTxId);
        }

        long replayedCount = outcomes.stream().filter(o -> o.result().replayed()).count();
        long nonReplayedCount = outcomes.stream().filter(o -> !o.result().replayed()).count();
        assertThat(replayedCount).isEqualTo(19L);
        assertThat(nonReplayedCount).isEqualTo(1L);

        assertThat(txRepository.findByIdempotencyKey(key)).isPresent();

        assertThat(testSupport.balance(userA.userId())).isEqualTo(99_500L);
        assertThat(testSupport.balance(userB.userId())).isEqualTo(500L);

        Long ledgerCount = jdbcTemplate.queryForObject(
                "select count(*) from ledger_entries where transaction_id = ?",
                Long.class,
                expectedTxId
        );
        assertThat(ledgerCount).isEqualTo(2L);

        if (!conflictsPossible()) {
            assertThat(transferFacade.getRetryCount() - initialRetries).isZero();
            assertThat(walletMetrics.getRetryCount(strategyName()) - initialMetricRetries).isZero();
        }
    }

    @Test
    @DisplayName("Same idempotency key with a different payload is rejected")
    void sameKeyDifferentPayloadIsRejected() {
        CreateUserResponse userA = testSupport.createFundedUser(100_000L);
        CreateUserResponse userB = testSupport.createUser();
        String key = testSupport.newKey();

        OperationResult first = transferFacade.transfer(new TransferCommand(userA.userId(), userB.userId(), 500L, key));
        assertThat(first.replayed()).isFalse();

        assertThat(testSupport.balance(userA.userId())).isEqualTo(99_500L);
        assertThat(testSupport.balance(userB.userId())).isEqualTo(500L);

        assertThatThrownBy(() -> transferFacade.transfer(new TransferCommand(userA.userId(), userB.userId(), 700L, key)))
                .isInstanceOf(IdempotencyKeyReusedException.class);

        assertThat(testSupport.balance(userA.userId())).isEqualTo(99_500L);
        assertThat(testSupport.balance(userB.userId())).isEqualTo(500L);
    }

    @Test
    @DisplayName("Top-up is idempotent when retried concurrently")
    void topUpIsIdempotent() throws Exception {
        CreateUserResponse user = testSupport.createUser();
        String key = testSupport.newKey();
        long topUpAmount = 5_000L;

        List<Callable<OperationResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> transferFacade.topUp(new TopUpCommand(user.userId(), topUpAmount, key)));
        }

        List<TestSupport.TaskOutcome<OperationResult>> outcomes = testSupport.runConcurrently(tasks, 10);

        assertThat(outcomes).allMatch(TestSupport.TaskOutcome::isSuccess);

        long nonReplayed = outcomes.stream().filter(o -> !o.result().replayed()).count();
        assertThat(nonReplayed).isEqualTo(1L);

        assertThat(testSupport.balance(user.userId())).isEqualTo(topUpAmount);

        Long topUpCount = jdbcTemplate.queryForObject(
                "select count(*) from wallet_transactions where idempotency_key = ? and type = 'TOPUP'",
                Long.class,
                key
        );
        assertThat(topUpCount).isEqualTo(1L);
    }

    // ==========================================
    // Validation Tests
    // ==========================================

    @Test
    @DisplayName("Self-transfer throws SelfTransferException")
    void selfTransferThrowsException() {
        CreateUserResponse user = testSupport.createFundedUser(10_000L);
        String key = testSupport.newKey();

        assertThatThrownBy(() -> transferFacade.transfer(new TransferCommand(user.userId(), user.userId(), 1_000L, key)))
                .isInstanceOf(SelfTransferException.class);
    }

    @Test
    @DisplayName("Amounts zero, negative, or above maximum throw InvalidAmountException")
    void invalidAmountThrowsException() {
        CreateUserResponse sender = testSupport.createFundedUser(50_000L);
        CreateUserResponse receiver = testSupport.createUser();

        long max = walletProperties.maxAmountPaise();

        assertThatThrownBy(() -> transferFacade.transfer(new TransferCommand(sender.userId(), receiver.userId(), 0L, testSupport.newKey())))
                .isInstanceOf(InvalidAmountException.class);

        assertThatThrownBy(() -> transferFacade.transfer(new TransferCommand(sender.userId(), receiver.userId(), -500L, testSupport.newKey())))
                .isInstanceOf(InvalidAmountException.class);

        assertThatThrownBy(() -> transferFacade.transfer(new TransferCommand(sender.userId(), receiver.userId(), max + 1, testSupport.newKey())))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("Unknown sender or receiver throws UserNotFoundException")
    void unknownUserThrowsException() {
        CreateUserResponse existing = testSupport.createFundedUser(10_000L);
        UUID randomId = UUID.randomUUID();

        assertThatThrownBy(() -> transferFacade.transfer(new TransferCommand(randomId, existing.userId(), 1_000L, testSupport.newKey())))
                .isInstanceOf(UserNotFoundException.class);

        assertThatThrownBy(() -> transferFacade.transfer(new TransferCommand(existing.userId(), randomId, 1_000L, testSupport.newKey())))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("Insufficient funds leaves balances untouched and does not consume the idempotency key")
    void insufficientFundsDoesNotConsumeKey() {
        CreateUserResponse sender = testSupport.createFundedUser(500L);
        CreateUserResponse receiver = testSupport.createUser();
        String key = testSupport.newKey();

        assertThatThrownBy(() -> transferFacade.transfer(new TransferCommand(sender.userId(), receiver.userId(), 1_000L, key)))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(testSupport.balance(sender.userId())).isEqualTo(500L);
        assertThat(testSupport.balance(receiver.userId())).isEqualTo(0L);

        assertThat(txRepository.findByIdempotencyKey(key)).isEmpty();

        Long ledgerCount = jdbcTemplate.queryForObject(
                "select count(*) from ledger_entries where wallet_id in (?, ?)",
                Long.class,
                sender.walletId(),
                receiver.walletId()
        );
        // sender has 1 top-up credit entry, receiver has 0
        assertThat(ledgerCount).isEqualTo(1L);

        // Fund sender with additional 1000L
        transferFacade.topUp(new TopUpCommand(sender.userId(), 1_000L, testSupport.newKey()));
        assertThat(testSupport.balance(sender.userId())).isEqualTo(1_500L);

        // Retrying the EXACT same key now succeeds!
        OperationResult retryResult = transferFacade.transfer(new TransferCommand(sender.userId(), receiver.userId(), 1_000L, key));
        assertThat(retryResult.replayed()).isFalse();
        assertThat(testSupport.balance(sender.userId())).isEqualTo(500L);
        assertThat(testSupport.balance(receiver.userId())).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("Exactly-equal balance transfer succeeds and leaves sender with zero balance")
    void exactBalanceTransferSucceeds() {
        long amount = 5_000L;
        CreateUserResponse sender = testSupport.createFundedUser(amount);
        CreateUserResponse receiver = testSupport.createUser();

        OperationResult result = transferFacade.transfer(new TransferCommand(sender.userId(), receiver.userId(), amount, testSupport.newKey()));

        assertThat(result.replayed()).isFalse();
        assertThat(testSupport.balance(sender.userId())).isEqualTo(0L);
        assertThat(testSupport.balance(receiver.userId())).isEqualTo(amount);
    }
}

