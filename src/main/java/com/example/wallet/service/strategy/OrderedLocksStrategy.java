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
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "wallet.transfer.strategy", havingValue = "ORDERED_LOCKS", matchIfMissing = true)
public class OrderedLocksStrategy extends AbstractTransferStrategy {

    private final WalletRepository walletRepository;

    public OrderedLocksStrategy(
            IdempotencyChecker idempotencyChecker,
            WalletTransactionRepository txRepository,
            LedgerEntryRepository ledgerRepository,
            WalletRepository walletRepository) {
        super(idempotencyChecker, txRepository, ledgerRepository);
        this.walletRepository = walletRepository;
    }

    @Override
    public OperationResult execute(MoneyMovement m) {
        // 1. Sort wallet IDs by UUID.compareTo; lock lower first then higher with findByIdForUpdate
        UUID firstId = m.fromWalletId().compareTo(m.toWalletId()) < 0 ? m.fromWalletId() : m.toWalletId();
        UUID secondId = firstId.equals(m.fromWalletId()) ? m.toWalletId() : m.fromWalletId();

        Wallet first = lockWallet(firstId);
        Wallet second = lockWallet(secondId);

        Wallet from = firstId.equals(m.fromWalletId()) ? first : second;
        Wallet to = (from == first) ? second : first;

        // 2. Re-check idempotency after locks
        Optional<OperationResult> replay = replayIfKeyExists(m);
        if (replay.isPresent()) {
            return replay.get();
        }

        // 3. Funds check against locked balance (skipped when sender is the system wallet)
        if (!from.isSystemWallet() && from.getBalancePaise() < m.amountPaise()) {
            throw new InsufficientFundsException(from.getUserId());
        }

        // 4. Update balances (Math.addExact on credit side), set updatedAt
        Instant now = Instant.now();
        from.setBalancePaise(from.getBalancePaise() - m.amountPaise());
        to.setBalancePaise(Math.addExact(to.getBalancePaise(), m.amountPaise()));
        from.setUpdatedAt(now);
        to.setUpdatedAt(now);

        // 5. record(...) with post-update balances
        return record(m, from.getBalancePaise(), to.getBalancePaise(), now);
    }

    private Wallet lockWallet(UUID id) {
        return walletRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalStateException("Wallet not found for id: " + id));
    }
}

