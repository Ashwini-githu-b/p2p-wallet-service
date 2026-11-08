package com.example.wallet.service;

import com.example.wallet.repository.WalletRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SystemWalletProvider {

    private final WalletRepository walletRepository;
    private UUID systemWalletId;

    public SystemWalletProvider(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @PostConstruct
    public void init() {
        this.systemWalletId = walletRepository.findSystemWalletId()
                .orElseThrow(() -> new IllegalStateException("Exactly one system wallet must exist in the database, but none was found."));
    }

    public UUID id() {
        if (systemWalletId == null) {
            init();
        }
        return systemWalletId;
    }
}

