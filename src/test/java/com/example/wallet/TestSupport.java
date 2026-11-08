package com.example.wallet;

import com.example.wallet.dto.CreateUserRequest;
import com.example.wallet.dto.CreateUserResponse;
import com.example.wallet.dto.TopUpCommand;
import com.example.wallet.repository.WalletRepository;
import com.example.wallet.service.TransferFacade;
import com.example.wallet.service.UserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class TestSupport {

    private final UserService userService;
    private final TransferFacade transferFacade;
    private final WalletRepository walletRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AtomicInteger userCounter = new AtomicInteger(1);

    public record TaskOutcome<T>(T result, Throwable exception) {
        public boolean isSuccess() {
            return exception == null;
        }
    }

    public TestSupport(
            UserService userService,
            TransferFacade transferFacade,
            WalletRepository walletRepository,
            JdbcTemplate jdbcTemplate) {
        this.userService = userService;
        this.transferFacade = transferFacade;
        this.walletRepository = walletRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public CreateUserResponse createUser() {
        int seq = userCounter.getAndIncrement();
        String uniqueId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return userService.createUser(new CreateUserRequest("User " + seq, "user" + seq + "_" + uniqueId + "@example.com"));
    }

    public CreateUserResponse createFundedUser(long paise) {
        CreateUserResponse user = createUser();
        transferFacade.topUp(new TopUpCommand(user.userId(), paise, newKey()));
        return user;
    }

    public String newKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public <T> List<TaskOutcome<T>> runConcurrently(List<Callable<T>> tasks, int threads) throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<TaskOutcome<T>>> futures = new ArrayList<>(tasks.size());
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    try {
                        return new TaskOutcome<>(task.call(), null);
                    } catch (Throwable t) {
                        return new TaskOutcome<>(null, t);
                    }
                }));
            }
            startGate.countDown();
            List<TaskOutcome<T>> outcomes = new ArrayList<>(futures.size());
            for (Future<TaskOutcome<T>> future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    public long balance(UUID userId) {
        return walletRepository.findByUserIdAndSystemWalletFalse(userId).orElseThrow().getBalancePaise();
    }

    public long sumBalances(List<UUID> userIds) {
        return userIds.stream().mapToLong(this::balance).sum();
    }

    public UUID walletId(UUID userId) {
        return walletRepository.findIdByUserId(userId).orElseThrow();
    }

    public long ledgerSum(UUID walletId) {
        Long sum = jdbcTemplate.queryForObject(
                "select coalesce(sum(case direction when 'CREDIT' then amount_paise else -amount_paise end),0) from ledger_entries where wallet_id = ?",
                Long.class,
                walletId
        );
        return sum != null ? sum : 0L;
    }

    public long countLedgerEntries(List<UUID> walletIds) {
        if (walletIds.isEmpty()) {
            return 0L;
        }
        String inSql = String.join(",", Collections.nCopies(walletIds.size(), "?"));
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from ledger_entries where wallet_id in (" + inSql + ")",
                Long.class,
                walletIds.toArray()
        );
        return count != null ? count : 0L;
    }
}

