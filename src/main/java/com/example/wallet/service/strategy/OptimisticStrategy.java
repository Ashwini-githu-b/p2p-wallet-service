package com.example.wallet.service.strategy;

import com.example.wallet.dto.OperationResult;
import com.example.wallet.entity.Wallet;
import com.example.wallet.exception.InsufficientFundsException;
import com.example.wallet.repository.LedgerEntryRepository;
import com.example.wallet.repository.WalletRepository;
import com.example.wallet.repository.WalletTransactionRepository;
import com.example.wallet.service.IdempotencyChecker;
import com.example.wallet.service.MoneyMovement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "wallet.transfer.strategy", havingValue = "OPTIMISTIC")
public class OptimisticStrategy extends AbstractTransferStrategy {

    private final WalletRepository walletRepository;

    public OptimisticStrategy(
            IdempotencyChecker idempotencyChecker,
            WalletTransactionRepository txRepository,
            LedgerEntryRepository ledgerRepository,
            WalletRepository walletRepository) {
        super(idempotencyChecker, txRepository, ledgerRepository);
        this.walletRepository = walletRepository;
    }

    @Override
    public String name() {
        return "OPTIMISTIC";
    }

    @Override
    public OperationResult execute(MoneyMovement m) {
        // 1. Plain reads of both wallets with findById (no locks; the @Version values are captured)
        Wallet from = walletRepository.findById(m.fromWalletId())
                .orElseThrow(() -> new IllegalStateException("Wallet not found: " + m.fromWalletId()));
        Wallet to = walletRepository.findById(m.toWalletId())
                .orElseThrow(() -> new IllegalStateException("Wallet not found: " + m.toWalletId()));

        // 2. If !from.isSystemWallet() && from.balance < amount: re-check idempotency key first, else throw
        if (!from.isSystemWallet() && from.getBalancePaise() < m.amountPaise()) {
            Optional<OperationResult> replay = replayIfKeyExists(m);
            if (replay.isPresent()) {
                return replay.get();
            }
            throw new InsufficientFundsException(from.getUserId());
        }

        // 3. Update balances and updatedAt
        Instant now = Instant.now();
        from.setBalancePaise(from.getBalancePaise() - m.amountPaise());
        to.setBalancePaise(Math.addExact(to.getBalancePaise(), m.amountPaise()));
        from.setUpdatedAt(now);
        to.setUpdatedAt(now);

        // 4. record(...) - saveAndFlush flushes entity version checks and transaction inserts
        return record(m, from.getBalancePaise(), to.getBalancePaise(), now);
    }
}

