package com.example.wallet.service;

import com.example.wallet.config.WalletProperties;
import com.example.wallet.dto.OperationResult;
import com.example.wallet.dto.TopUpCommand;
import com.example.wallet.dto.TransferCommand;
import com.example.wallet.exception.ConcurrentUpdateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Component
public class TransferFacade {

    private static final Logger log = LoggerFactory.getLogger(TransferFacade.class);

    private final TransferService transferService;
    private final WalletProperties walletProperties;
    private final AtomicLong retryCounter = new AtomicLong(0);

    public TransferFacade(TransferService transferService, WalletProperties walletProperties) {
        this.transferService = transferService;
        this.walletProperties = walletProperties;
    }

    public OperationResult transfer(TransferCommand cmd) {
        return execute(
                () -> transferService.transfer(cmd),
                () -> transferService.replayIfExists(cmd)
        );
    }

    public OperationResult topUp(TopUpCommand cmd) {
        return execute(
                () -> transferService.topUp(cmd),
                () -> transferService.replayIfExists(cmd)
        );
    }

    public long getRetryCount() {
        return retryCounter.get();
    }

    public void resetRetryCount() {
        retryCounter.set(0);
    }

    private OperationResult execute(
            Supplier<OperationResult> call,
            Supplier<Optional<OperationResult>> replayIfExists) {

        int maxRetries = 10;
        long baseBackoffMs = 2;
        long maxBackoffMs = 50;

        if (walletProperties.transfer() != null && walletProperties.transfer().retry() != null) {
            maxRetries = walletProperties.transfer().retry().maxRetries();
            baseBackoffMs = walletProperties.transfer().retry().baseBackoffMs();
            maxBackoffMs = walletProperties.transfer().retry().maxBackoffMs();
        }

        int attempt = 0;
        while (true) {
            try {
                return call.get();
            } catch (DataIntegrityViolationException e) {
                // Idempotency race (as in V1): unique key constraint caught
                if (!isIdempotencyKeyViolation(e)) {
                    throw e;
                }
                return replayIfExists.get().orElseThrow(() -> e);
            } catch (ObjectOptimisticLockingFailureException | CannotAcquireLockException e) {
                if (++attempt > maxRetries) {
                    throw new ConcurrentUpdateException();
                }
                retryCounter.incrementAndGet();
                long backoff = computeFullJitter(attempt, baseBackoffMs, maxBackoffMs);
                sleepWithJitter(backoff);
            }
        }
    }

    private long computeFullJitter(int attempt, long baseBackoffMs, long maxBackoffMs) {
        long exponential = baseBackoffMs * (1L << Math.min(attempt, 30));
        long ceiling = Math.min(maxBackoffMs, exponential);
        if (ceiling <= 0) {
            return 0;
        }
        return ThreadLocalRandom.current().nextLong(ceiling + 1);
    }

    private void sleepWithJitter(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted during retry backoff", e);
        }
    }

    private boolean isIdempotencyKeyViolation(DataIntegrityViolationException e) {
        Throwable current = e;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("uq_wallet_transactions_idempotency_key")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
