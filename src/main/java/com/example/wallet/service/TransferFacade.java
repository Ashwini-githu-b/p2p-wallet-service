package com.example.wallet.service;

import com.example.wallet.config.WalletProperties;
import com.example.wallet.dto.OperationResult;
import com.example.wallet.dto.TopUpCommand;
import com.example.wallet.dto.TransferCommand;
import com.example.wallet.exception.ConcurrentUpdateException;
import com.example.wallet.exception.InsufficientFundsException;
import com.example.wallet.exception.WalletException;
import com.example.wallet.metrics.WalletMetrics;
import com.example.wallet.service.strategy.TransferStrategy;
import io.micrometer.core.instrument.Timer;
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
    private final TransferStrategy transferStrategy;
    private final WalletMetrics walletMetrics;
    private final AtomicLong retryCounter = new AtomicLong(0);

    public TransferFacade(
            TransferService transferService,
            WalletProperties walletProperties,
            TransferStrategy transferStrategy,
            WalletMetrics walletMetrics) {
        this.transferService = transferService;
        this.walletProperties = walletProperties;
        this.transferStrategy = transferStrategy;
        this.walletMetrics = walletMetrics;
    }

    public OperationResult transfer(TransferCommand cmd) {
        return execute(
                "TRANSFER",
                () -> transferService.transfer(cmd),
                () -> transferService.replayIfExists(cmd)
        );
    }

    public OperationResult topUp(TopUpCommand cmd) {
        return execute(
                "TOPUP",
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
            String type,
            Supplier<OperationResult> call,
            Supplier<Optional<OperationResult>> replayIfExists) {

        String strategyName = transferStrategy.name();
        Timer.Sample sample = walletMetrics.startTimer();
        String outcome = "error";

        int maxRetries = 10;
        long baseBackoffMs = 2;
        long maxBackoffMs = 50;

        if (walletProperties.transfer() != null && walletProperties.transfer().retry() != null) {
            maxRetries = walletProperties.transfer().retry().maxRetries();
            baseBackoffMs = walletProperties.transfer().retry().baseBackoffMs();
            maxBackoffMs = walletProperties.transfer().retry().maxBackoffMs();
        }

        int attempt = 0;
        try {
            while (true) {
                try {
                    OperationResult result = call.get();
                    outcome = result.replayed() ? "replayed" : "success";
                    return result;
                } catch (DataIntegrityViolationException e) {
                    // Idempotency race (as in V1): unique key constraint caught
                    if (!isIdempotencyKeyViolation(e)) {
                        throw e;
                    }
                    OperationResult replay = replayIfExists.get().orElseThrow(() -> e);
                    outcome = "replayed";
                    return replay;
                } catch (ObjectOptimisticLockingFailureException | CannotAcquireLockException e) {
                    if (++attempt > maxRetries) {
                        throw new ConcurrentUpdateException();
                    }
                    retryCounter.incrementAndGet();
                    walletMetrics.retry(strategyName);
                    long backoff = computeFullJitter(attempt, baseBackoffMs, maxBackoffMs);
                    sleepWithJitter(backoff);
                }
            }
        } catch (InsufficientFundsException e) {
            outcome = "insufficient_funds";
            throw e;
        } catch (ConcurrentUpdateException e) {
            outcome = "conflict_exhausted";
            throw e;
        } catch (WalletException e) {
            outcome = "invalid";
            throw e;
        } catch (Throwable t) {
            outcome = "error";
            throw t;
        } finally {
            walletMetrics.recordOperation(sample, type, strategyName, outcome);
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
