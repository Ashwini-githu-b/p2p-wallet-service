package com.example.wallet.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI walletOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Wallet Service API")
                        .version("v1")
                        .description("High-performance, concurrency-safe digital wallet API providing atomic peer-to-peer transfers, strict idempotency, and a balanced double-entry ledger."));
    }
}

