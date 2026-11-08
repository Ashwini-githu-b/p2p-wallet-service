package com.example.wallet;

import com.example.wallet.dto.CreateUserRequest;
import com.example.wallet.dto.CreateUserResponse;
import com.example.wallet.dto.ErrorResponse;
import com.example.wallet.dto.PageResponse;
import com.example.wallet.dto.TopUpRequest;
import com.example.wallet.dto.TransactionResponse;
import com.example.wallet.dto.TransferRequest;
import com.example.wallet.dto.WalletResponse;
import com.example.wallet.entity.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiSmokeTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("End-to-end API smoke test covering all endpoints, idempotency replay, and error handling")
    void apiSmokeTest() {
        // 1. Create User 1
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String email1 = "smoke1_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        HttpEntity<CreateUserRequest> createUserReq1 = new HttpEntity<>(new CreateUserRequest("Alice Smoke", email1), headers);
        ResponseEntity<CreateUserResponse> user1Res = restTemplate.postForEntity("/api/v1/users", createUserReq1, CreateUserResponse.class);
        assertThat(user1Res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        CreateUserResponse user1 = user1Res.getBody();
        assertThat(user1).isNotNull();
        assertThat(user1.balancePaise()).isEqualTo(0L);

        // 2. Create User 2
        String email2 = "smoke2_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        HttpEntity<CreateUserRequest> createUserReq2 = new HttpEntity<>(new CreateUserRequest("Bob Smoke", email2), headers);
        ResponseEntity<CreateUserResponse> user2Res = restTemplate.postForEntity("/api/v1/users", createUserReq2, CreateUserResponse.class);
        assertThat(user2Res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        CreateUserResponse user2 = user2Res.getBody();
        assertThat(user2).isNotNull();
        assertThat(user2.balancePaise()).isEqualTo(0L);

        // 3. Top up User 1 (201 Created, then replay returns 200 with Idempotent-Replay: true)
        String topUpKey = "topup-key-" + UUID.randomUUID().toString().replace("-", "");
        HttpHeaders topUpHeaders = new HttpHeaders();
        topUpHeaders.setContentType(MediaType.APPLICATION_JSON);
        topUpHeaders.set("Idempotency-Key", topUpKey);

        HttpEntity<TopUpRequest> topUpReq = new HttpEntity<>(new TopUpRequest(50_000L), topUpHeaders);
        ResponseEntity<TransactionResponse> topUpRes = restTemplate.postForEntity(
                "/api/v1/wallets/" + user1.userId() + "/topup",
                topUpReq,
                TransactionResponse.class
        );
        assertThat(topUpRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TransactionResponse topUpTx = topUpRes.getBody();
        assertThat(topUpTx).isNotNull();
        assertThat(topUpTx.type()).isEqualTo(TransactionType.TOPUP);
        assertThat(topUpTx.amountPaise()).isEqualTo(50_000L);
        assertThat(topUpTx.fromUserId()).isNull();
        assertThat(topUpTx.toUserId()).isEqualTo(user1.userId());

        // Replay Top Up
        ResponseEntity<TransactionResponse> topUpReplayRes = restTemplate.postForEntity(
                "/api/v1/wallets/" + user1.userId() + "/topup",
                topUpReq,
                TransactionResponse.class
        );
        assertThat(topUpReplayRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(topUpReplayRes.getHeaders().getFirst("Idempotent-Replay")).isEqualTo("true");
        assertThat(topUpReplayRes.getBody()).isNotNull();
        assertThat(topUpReplayRes.getBody().transactionId()).isEqualTo(topUpTx.transactionId());

        // 4. Transfer from User 1 to User 2 (201 Created)
        String transferKey = "tx-key-" + UUID.randomUUID().toString().replace("-", "");
        HttpHeaders transferHeaders = new HttpHeaders();
        transferHeaders.setContentType(MediaType.APPLICATION_JSON);
        transferHeaders.set("Idempotency-Key", transferKey);

        TransferRequest transferReqBody = new TransferRequest(user1.userId(), user2.userId(), 20_000L);
        HttpEntity<TransferRequest> transferReq = new HttpEntity<>(transferReqBody, transferHeaders);

        ResponseEntity<TransactionResponse> transferRes = restTemplate.postForEntity(
                "/api/v1/transfers",
                transferReq,
                TransactionResponse.class
        );
        assertThat(transferRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TransactionResponse transferTx = transferRes.getBody();
        assertThat(transferTx).isNotNull();
        assertThat(transferTx.type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(transferTx.amountPaise()).isEqualTo(20_000L);
        assertThat(transferTx.fromUserId()).isEqualTo(user1.userId());
        assertThat(transferTx.toUserId()).isEqualTo(user2.userId());

        // 5. Missing Idempotency-Key returns 400 MISSING_IDEMPOTENCY_KEY
        HttpHeaders noKeyHeaders = new HttpHeaders();
        noKeyHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TransferRequest> noKeyReq = new HttpEntity<>(
                new TransferRequest(user1.userId(), user2.userId(), 5_000L),
                noKeyHeaders
        );
        ResponseEntity<ErrorResponse> noKeyRes = restTemplate.postForEntity(
                "/api/v1/transfers",
                noKeyReq,
                ErrorResponse.class
        );
        assertThat(noKeyRes.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(noKeyRes.getBody()).isNotNull();
        assertThat(noKeyRes.getBody().code()).isEqualTo("MISSING_IDEMPOTENCY_KEY");

        // 6. Insufficient funds returns 422 INSUFFICIENT_FUNDS
        HttpHeaders overdraftHeaders = new HttpHeaders();
        overdraftHeaders.setContentType(MediaType.APPLICATION_JSON);
        overdraftHeaders.set("Idempotency-Key", "overdraft-key-" + UUID.randomUUID().toString().replace("-", ""));
        HttpEntity<TransferRequest> overdraftReq = new HttpEntity<>(
                new TransferRequest(user1.userId(), user2.userId(), 100_000L),
                overdraftHeaders
        );
        ResponseEntity<ErrorResponse> overdraftRes = restTemplate.postForEntity(
                "/api/v1/transfers",
                overdraftReq,
                ErrorResponse.class
        );
        assertThat(overdraftRes.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(overdraftRes.getBody()).isNotNull();
        assertThat(overdraftRes.getBody().code()).isEqualTo("INSUFFICIENT_FUNDS");

        // 7. GET /wallets/{id} shows correct balances
        ResponseEntity<WalletResponse> user1WalletRes = restTemplate.getForEntity(
                "/api/v1/wallets/" + user1.userId(),
                WalletResponse.class
        );
        assertThat(user1WalletRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(user1WalletRes.getBody()).isNotNull();
        assertThat(user1WalletRes.getBody().balancePaise()).isEqualTo(30_000L);

        ResponseEntity<WalletResponse> user2WalletRes = restTemplate.getForEntity(
                "/api/v1/wallets/" + user2.userId(),
                WalletResponse.class
        );
        assertThat(user2WalletRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(user2WalletRes.getBody()).isNotNull();
        assertThat(user2WalletRes.getBody().balancePaise()).isEqualTo(20_000L);

        // 8. History returns expected items
        ResponseEntity<Map> historyRes = restTemplate.getForEntity(
                "/api/v1/wallets/" + user1.userId() + "/transactions?page=0&size=20",
                Map.class
        );
        assertThat(historyRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(historyRes.getBody()).isNotNull();
        assertThat(historyRes.getBody().get("totalElements")).isEqualTo(2);

        // 9. Unknown user returns 404 USER_NOT_FOUND
        UUID randomUserId = UUID.randomUUID();
        ResponseEntity<ErrorResponse> notFoundRes = restTemplate.getForEntity(
                "/api/v1/wallets/" + randomUserId,
                ErrorResponse.class
        );
        assertThat(notFoundRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFoundRes.getBody()).isNotNull();
        assertThat(notFoundRes.getBody().code()).isEqualTo("USER_NOT_FOUND");
    }
}

