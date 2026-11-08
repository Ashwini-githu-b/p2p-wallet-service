package com.example.wallet.controller;

import com.example.wallet.config.WalletProperties;
import com.example.wallet.dto.ReconciliationResponse;
import com.example.wallet.entity.ReconciliationRun;
import com.example.wallet.exception.AdminDisabledException;
import com.example.wallet.exception.NoReconciliationRunException;
import com.example.wallet.exception.ReconciliationInProgressException;
import com.example.wallet.exception.UnauthorizedException;
import com.example.wallet.service.ReconciliationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final ReconciliationService reconciliationService;
    private final WalletProperties walletProperties;

    public AdminController(ReconciliationService reconciliationService, WalletProperties walletProperties) {
        this.reconciliationService = reconciliationService;
        this.walletProperties = walletProperties;
    }

    @GetMapping("/reconciliation/latest")
    public ResponseEntity<ReconciliationResponse> getLatest() {
        ReconciliationRun run = reconciliationService.getLatestRun()
                .orElseThrow(NoReconciliationRunException::new);
        return ResponseEntity.ok(ReconciliationResponse.from(run));
    }

    @PostMapping("/reconciliation/run")
    public ResponseEntity<ReconciliationResponse> triggerRun(
            @RequestHeader(value = "X-Admin-Token", required = false) String adminTokenHeader) {

        String configuredToken = walletProperties.adminToken();
        if (configuredToken == null || configuredToken.isBlank()) {
            throw new AdminDisabledException();
        }

        if (adminTokenHeader == null || !MessageDigest.isEqual(
                configuredToken.getBytes(StandardCharsets.UTF_8),
                adminTokenHeader.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException();
        }

        ReconciliationRun run = reconciliationService.runOnce()
                .orElseThrow(ReconciliationInProgressException::new);

        return ResponseEntity.ok(ReconciliationResponse.from(run));
    }
}

