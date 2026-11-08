package com.example.wallet.service;

import com.example.wallet.dto.HistoryItem;
import com.example.wallet.dto.PageResponse;
import com.example.wallet.dto.WalletResponse;
import com.example.wallet.entity.Wallet;
import com.example.wallet.exception.UserNotFoundException;
import com.example.wallet.repository.LedgerEntryRepository;
import com.example.wallet.repository.WalletRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public WalletService(WalletRepository walletRepository, LedgerEntryRepository ledgerEntryRepository) {
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID userId) {
        Wallet wallet = walletRepository.findByUserIdAndSystemWalletFalse(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return new WalletResponse(wallet.getUserId(), wallet.getId(), wallet.getBalancePaise());
    }

    @Transactional(readOnly = true)
    public PageResponse<HistoryItem> getHistory(UUID userId, int page, int size) {
        UUID walletId = walletRepository.findIdByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        Page<HistoryItem> historyPage = ledgerEntryRepository.findHistory(walletId, PageRequest.of(page, size));
        return new PageResponse<>(
                historyPage.getContent(),
                historyPage.getNumber(),
                historyPage.getSize(),
                historyPage.getTotalElements()
        );
    }
}

