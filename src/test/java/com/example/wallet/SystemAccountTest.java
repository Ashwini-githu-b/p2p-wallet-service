package com.example.wallet;

import com.example.wallet.dto.CreateUserResponse;
import com.example.wallet.dto.TopUpCommand;
import com.example.wallet.dto.TransferCommand;
import com.example.wallet.exception.UserNotFoundException;
import com.example.wallet.service.TransferFacade;
import com.example.wallet.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SystemAccountTest extends AbstractIntegrationTest {

    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SYSTEM_WALLET_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private TestSupport testSupport;

    @Autowired
    private TransferFacade transferFacade;

    @Autowired
    private WalletService walletService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Global balance sum across all wallets including system is always exactly 0")
    void globalBalanceSumIsZeroAfterTopUpsAndTransfers() {
        CreateUserResponse userA = testSupport.createFundedUser(50_000L);
        CreateUserResponse userB = testSupport.createFundedUser(30_000L);

        // Perform a transfer between users
        transferFacade.transfer(new TransferCommand(userA.userId(), userB.userId(), 15_000L, testSupport.newKey()));

        // Perform another top-up
        transferFacade.topUp(new TopUpCommand(userA.userId(), 10_000L, testSupport.newKey()));

        // Assert SUM(balance_paise) over ALL wallets (including system) = 0
        Long totalSum = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(balance_paise), 0) FROM wallets", Long.class);
        assertThat(totalSum).isEqualTo(0L);

        // System wallet balance = - (sum of all user-wallet balances)
        Long systemBalance = jdbcTemplate.queryForObject(
                "SELECT balance_paise FROM wallets WHERE id = ?", Long.class, SYSTEM_WALLET_ID);
        Long userWalletsSum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(balance_paise), 0) FROM wallets WHERE is_system = false", Long.class);

        assertThat(systemBalance).isNotNull();
        assertThat(userWalletsSum).isNotNull();
        assertThat(systemBalance).isEqualTo(-userWalletsSum);
    }

    @Test
    @DisplayName("Public API never touches the system user: returns 404 USER_NOT_FOUND")
    void publicApiNeverTouchesSystemUser() {
        CreateUserResponse normalUser = testSupport.createFundedUser(10_000L);

        // GET /wallets/{systemUserId} -> 404 USER_NOT_FOUND
        assertThatThrownBy(() -> walletService.getWallet(SYSTEM_USER_ID))
                .isInstanceOf(UserNotFoundException.class);

        // Transfer FROM system user ID -> 404 USER_NOT_FOUND
        assertThatThrownBy(() -> transferFacade.transfer(
                new TransferCommand(SYSTEM_USER_ID, normalUser.userId(), 1000L, testSupport.newKey())))
                .isInstanceOf(UserNotFoundException.class);

        // Transfer TO system user ID -> 404 USER_NOT_FOUND
        assertThatThrownBy(() -> transferFacade.transfer(
                new TransferCommand(normalUser.userId(), SYSTEM_USER_ID, 1000L, testSupport.newKey())))
                .isInstanceOf(UserNotFoundException.class);

        // Top-up for system user ID -> 404 USER_NOT_FOUND
        assertThatThrownBy(() -> transferFacade.topUp(
                new TopUpCommand(SYSTEM_USER_ID, 1000L, testSupport.newKey())))
                .isInstanceOf(UserNotFoundException.class);
    }
}

