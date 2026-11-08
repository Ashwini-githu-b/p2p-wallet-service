package com.example.wallet.repository;

import com.example.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    @Query("select w.id from Wallet w where w.userId = :userId and w.systemWallet = false")
    Optional<UUID> findIdByUserId(@Param("userId") UUID userId);

    @Query("select w.userId from Wallet w where w.id = :id")
    Optional<UUID> findUserIdById(@Param("id") UUID id);

    Optional<Wallet> findByUserIdAndSystemWalletFalse(UUID userId);

    @Query("select w.id from Wallet w where w.systemWallet = true")
    Optional<UUID> findSystemWalletId();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);
}
