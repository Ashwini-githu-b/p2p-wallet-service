package com.example.wallet;

import com.example.wallet.controller.WalletController;
import com.example.wallet.dto.CreateUserResponse;
import com.example.wallet.dto.HistoryItem;
import com.example.wallet.dto.PageResponse;
import com.example.wallet.dto.TopUpCommand;
import com.example.wallet.dto.TransferCommand;
import com.example.wallet.entity.EntryDirection;
import com.example.wallet.entity.TransactionType;
import com.example.wallet.service.TransferFacade;
import com.example.wallet.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestSupport testSupport;

    @Autowired
    private TransferFacade transferFacade;

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletController walletController;

    @Test
    @DisplayName("Transaction history is ordered newest-first with correct balances and counterparty information")
    void historyOrderAndContentAreCorrect() {
        CreateUserResponse userA = testSupport.createUser();
        CreateUserResponse userB = testSupport.createUser();
        CreateUserResponse userC = testSupport.createFundedUser(20_000L);

        // 1. Top-up A with 10_000
        transferFacade.topUp(new TopUpCommand(userA.userId(), 10_000L, testSupport.newKey()));
        // 2. A -> B 2_000
        transferFacade.transfer(new TransferCommand(userA.userId(), userB.userId(), 2_000L, testSupport.newKey()));
        // 3. C -> A 3_000
        transferFacade.transfer(new TransferCommand(userC.userId(), userA.userId(), 3_000L, testSupport.newKey()));
        // 4. A -> C 1_000
        transferFacade.transfer(new TransferCommand(userA.userId(), userC.userId(), 1_000L, testSupport.newKey()));
        // 5. A -> B 4_000
        transferFacade.transfer(new TransferCommand(userA.userId(), userB.userId(), 4_000L, testSupport.newKey()));

        PageResponse<HistoryItem> fullHistory = walletService.getHistory(userA.userId(), 0, 10);
        List<HistoryItem> items = fullHistory.items();

        assertThat(items).hasSize(5);
        assertThat(fullHistory.totalElements()).isEqualTo(5);

        // Newest first:
        // Item 0: A -> B 4000 DEBIT
        assertThat(items.get(0).type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(items.get(0).direction()).isEqualTo(EntryDirection.DEBIT);
        assertThat(items.get(0).amountPaise()).isEqualTo(4_000L);
        assertThat(items.get(0).balanceAfterPaise()).isEqualTo(6_000L);
        assertThat(items.get(0).counterpartyUserId()).isEqualTo(userB.userId());

        // Item 1: A -> C 1000 DEBIT
        assertThat(items.get(1).type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(items.get(1).direction()).isEqualTo(EntryDirection.DEBIT);
        assertThat(items.get(1).amountPaise()).isEqualTo(1_000L);
        assertThat(items.get(1).balanceAfterPaise()).isEqualTo(10_000L);
        assertThat(items.get(1).counterpartyUserId()).isEqualTo(userC.userId());

        // Item 2: C -> A 3000 CREDIT
        assertThat(items.get(2).type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(items.get(2).direction()).isEqualTo(EntryDirection.CREDIT);
        assertThat(items.get(2).amountPaise()).isEqualTo(3_000L);
        assertThat(items.get(2).balanceAfterPaise()).isEqualTo(11_000L);
        assertThat(items.get(2).counterpartyUserId()).isEqualTo(userC.userId());

        // Item 3: A -> B 2000 DEBIT
        assertThat(items.get(3).type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(items.get(3).direction()).isEqualTo(EntryDirection.DEBIT);
        assertThat(items.get(3).amountPaise()).isEqualTo(2_000L);
        assertThat(items.get(3).balanceAfterPaise()).isEqualTo(8_000L);
        assertThat(items.get(3).counterpartyUserId()).isEqualTo(userB.userId());

        // Item 4: TOPUP 10000 CREDIT
        assertThat(items.get(4).type()).isEqualTo(TransactionType.TOPUP);
        assertThat(items.get(4).direction()).isEqualTo(EntryDirection.CREDIT);
        assertThat(items.get(4).amountPaise()).isEqualTo(10_000L);
        assertThat(items.get(4).balanceAfterPaise()).isEqualTo(10_000L);
        assertThat(items.get(4).counterpartyUserId()).isNull();

        // Test Pagination: size=2 over 5 entries yields pages of 2, 2, 1
        PageResponse<HistoryItem> page0 = walletService.getHistory(userA.userId(), 0, 2);
        assertThat(page0.items()).hasSize(2);
        assertThat(page0.page()).isEqualTo(0);
        assertThat(page0.totalElements()).isEqualTo(5);
        assertThat(page0.items().get(0).amountPaise()).isEqualTo(4_000L);
        assertThat(page0.items().get(1).amountPaise()).isEqualTo(1_000L);

        PageResponse<HistoryItem> page1 = walletService.getHistory(userA.userId(), 1, 2);
        assertThat(page1.items()).hasSize(2);
        assertThat(page1.page()).isEqualTo(1);
        assertThat(page1.totalElements()).isEqualTo(5);
        assertThat(page1.items().get(0).amountPaise()).isEqualTo(3_000L);
        assertThat(page1.items().get(1).amountPaise()).isEqualTo(2_000L);

        PageResponse<HistoryItem> page2 = walletService.getHistory(userA.userId(), 2, 2);
        assertThat(page2.items()).hasSize(1);
        assertThat(page2.page()).isEqualTo(2);
        assertThat(page2.totalElements()).isEqualTo(5);
        assertThat(page2.items().get(0).amountPaise()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("Invalid pagination parameters are rejected")
    void invalidPaginationParametersAreRejected() {
        CreateUserResponse user = testSupport.createUser();

        assertThatThrownBy(() -> walletController.getHistory(user.userId(), -1, 20))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> walletController.getHistory(user.userId(), 0, 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> walletController.getHistory(user.userId(), 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

