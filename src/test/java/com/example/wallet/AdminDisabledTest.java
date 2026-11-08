package com.example.wallet;

import com.example.wallet.dto.ErrorResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "wallet.admin-token=",
                "wallet.reconciliation.enabled=false"
        }
)
class AdminDisabledTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("When admin token is blank, POST /reconciliation/run returns 403 ADMIN_DISABLED")
    void adminDisabledReturns403() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", "any-token");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/admin/reconciliation/run",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ADMIN_DISABLED");
    }
}

