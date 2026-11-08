package com.example.wallet;

import com.example.wallet.dto.ErrorResponse;
import com.example.wallet.dto.ReconciliationResponse;
import com.example.wallet.service.ReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "wallet.admin-token=test-token",
                "wallet.reconciliation.enabled=false"
        }
)
class AdminApiTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    @DisplayName("Admin reconciliation API validates tokens and exposes latest status")
    void adminReconciliationEndpoints() {
        // Run once via service to guarantee a run exists
        reconciliationService.runOnce();

        // 1. GET /api/v1/admin/reconciliation/latest returns 200 OK
        ResponseEntity<ReconciliationResponse> getLatestResp = restTemplate.getForEntity(
                "/api/v1/admin/reconciliation/latest", ReconciliationResponse.class);
        assertThat(getLatestResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getLatestResp.getBody()).isNotNull();
        assertThat(getLatestResp.getBody().status()).isEqualTo("OK");

        // 2. POST /api/v1/admin/reconciliation/run without header -> 401 UNAUTHORIZED
        ResponseEntity<ErrorResponse> noHeaderResp = restTemplate.postForEntity(
                "/api/v1/admin/reconciliation/run", null, ErrorResponse.class);
        assertThat(noHeaderResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(noHeaderResp.getBody()).isNotNull();
        assertThat(noHeaderResp.getBody().code()).isEqualTo("UNAUTHORIZED");

        // 3. POST with wrong token -> 401 UNAUTHORIZED
        HttpHeaders wrongHeaders = new HttpHeaders();
        wrongHeaders.set("X-Admin-Token", "wrong-secret-token");
        ResponseEntity<ErrorResponse> wrongTokenResp = restTemplate.exchange(
                "/api/v1/admin/reconciliation/run",
                HttpMethod.POST,
                new HttpEntity<>(wrongHeaders),
                ErrorResponse.class
        );
        assertThat(wrongTokenResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongTokenResp.getBody()).isNotNull();
        assertThat(wrongTokenResp.getBody().code()).isEqualTo("UNAUTHORIZED");

        // 4. POST with correct token -> 200 OK and runs reconciliation
        HttpHeaders correctHeaders = new HttpHeaders();
        correctHeaders.set("X-Admin-Token", "test-token");
        ResponseEntity<ReconciliationResponse> runResp = restTemplate.exchange(
                "/api/v1/admin/reconciliation/run",
                HttpMethod.POST,
                new HttpEntity<>(correctHeaders),
                ReconciliationResponse.class
        );
        assertThat(runResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(runResp.getBody()).isNotNull();
        assertThat(runResp.getBody().status()).isEqualTo("OK");
        assertThat(runResp.getBody().runId()).isNotNull();
    }
}
