package com.example.wallet.repository;

import com.example.wallet.dto.HistoryItem;
import com.example.wallet.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    @Query(value = """
        select new com.example.wallet.dto.HistoryItem(
            le.transactionId, t.type, le.direction, le.amountPaise,
            le.balanceAfterPaise, cw.userId, le.createdAt)
        from LedgerEntry le
          join WalletTransaction t on t.id = le.transactionId
          left join Wallet cw on cw.id = le.counterpartyWalletId
        where le.walletId = :walletId
        order by le.createdAt desc, le.id desc
        """,
        countQuery = "select count(le) from LedgerEntry le where le.walletId = :walletId")
    Page<HistoryItem> findHistory(@Param("walletId") UUID walletId, Pageable pageable);
}

