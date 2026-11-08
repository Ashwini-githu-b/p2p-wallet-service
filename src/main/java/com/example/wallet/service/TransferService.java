package com.example.wallet.service;

import com.example.wallet.config.WalletProperties;
import com.example.wallet.dto.OperationResult;
import com.example.wallet.dto.TopUpCommand;
import com.example.wallet.dto.TransferCommand;
import com.example.wallet.entity.TransactionType;
import com.example.wallet.exception.InvalidAmountException;
import com.example.wallet.exception.SelfTransferException;
import com.example.wallet.exception.UserNotFoundException;
import com.example.wallet.repository.WalletRepository;
import com.example.wallet.service.strategy.TransferStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService {

    private final WalletRepository walletRepository;
    private final WalletProperties walletProperties;
    private final SystemWalletProvider systemWalletProvider;
    private final IdempotencyChecker idempotencyChecker;
    private final TransferStrategy strategy;

    public TransferService(
            WalletRepository walletRepository,
            WalletProperties walletProperties,
            SystemWalletProvider systemWalletProvider,
            IdempotencyChecker idempotencyChecker,
            TransferStrategy strategy) {
        this.walletRepository = walletRepository;
        this.walletProperties = walletProperties;
        this.systemWalletProvider = systemWalletProvider;
        this.idempotencyChecker = idempotencyChecker;
        this.strategy = strategy;
    }

    @Transactional
    public OperationResult transfer(TransferCommand cmd) {
        validateAmount(cmd.amountPaise());
        if (cmd.fromUserId().equals(cmd.toUserId())) {
            throw new SelfTransferException();
        }

        UUID fromWalletId = walletRepository.findIdByUserId(cmd.fromUserId())
                .orElseThrow(() -> new UserNotFoundException(cmd.fromUserId()));
        UUID toWalletId = walletRepository.findIdByUserId(cmd.toUserId())
                .orElseThrow(() -> new UserNotFoundException(cmd.toUserId()));

        MoneyMovement m = new MoneyMovement(
                TransactionType.TRANSFER, fromWalletId, toWalletId, cmd.amountPaise(), cmd.idempotencyKey());

        Optional<OperationResult> replay = idempotencyChecker.findReplay(m);
        if (replay.isPresent()) {
            return replay.get();
        }

        return strategy.execute(m);
    }

    @Transactional
    public OperationResult topUp(TopUpCommand cmd) {
        validateAmount(cmd.amountPaise());

        UUID userWalletId = walletRepository.findIdByUserId(cmd.userId())
                .orElseThrow(() -> new UserNotFoundException(cmd.userId()));
        UUID systemWalletId = systemWalletProvider.id();

        MoneyMovement m = new MoneyMovement(
                TransactionType.TOPUP, systemWalletId, userWalletId, cmd.amountPaise(), cmd.idempotencyKey());

        Optional<OperationResult> replay = idempotencyChecker.findReplay(m);
        if (replay.isPresent()) {
            return replay.get();
        }

        return strategy.execute(m);
    }

    @Transactional(readOnly = true)
    public Optional<OperationResult> replayIfExists(TransferCommand cmd) {
        UUID fromWalletId = walletRepository.findIdByUserId(cmd.fromUserId())
                .orElseThrow(() -> new UserNotFoundException(cmd.fromUserId()));
        UUID toWalletId = walletRepository.findIdByUserId(cmd.toUserId())
                .orElseThrow(() -> new UserNotFoundException(cmd.toUserId()));
        return idempotencyChecker.findReplay(new MoneyMovement(
                TransactionType.TRANSFER, fromWalletId, toWalletId, cmd.amountPaise(), cmd.idempotencyKey()
        ));
    }

    @Transactional(readOnly = true)
    public Optional<OperationResult> replayIfExists(TopUpCommand cmd) {
        UUID userWalletId = walletRepository.findIdByUserId(cmd.userId())
                .orElseThrow(() -> new UserNotFoundException(cmd.userId()));
        UUID systemWalletId = systemWalletProvider.id();
        return idempotencyChecker.findReplay(new MoneyMovement(
                TransactionType.TOPUP, systemWalletId, userWalletId, cmd.amountPaise(), cmd.idempotencyKey()
        ));
    }

    private void validateAmount(long amountPaise) {
        if (amountPaise < 1 || amountPaise > walletProperties.maxAmountPaise()) {
            throw new InvalidAmountException("Amount must be between 1 and " + walletProperties.maxAmountPaise() + " paise: " + amountPaise);
        }
    }
}
