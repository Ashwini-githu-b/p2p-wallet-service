package com.example.wallet.controller;

import com.example.wallet.config.WalletProperties;
import com.example.wallet.dto.ReconciliationResponse;
import com.example.wallet.entity.ReconciliationRun;
import com.example.wallet.exception.AdminDisabledException;
import com.example.wallet.exception.NoReconciliationRunException;
import com.example.wallet.exception.ReconciliationInProgressException;
import com.example.wallet.exception.UnauthorizedException;
import com.example.wallet.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin", description = "Administrative operations and reconciliation ledger audits")
public class AdminController {

    private final ReconciliationService reconciliationService;
    private final WalletProperties walletProperties;

    public AdminController(ReconciliationService reconciliationService, WalletProperties walletProperties) {
        this.reconciliationService = reconciliationService;
        this.walletProperties = walletProperties;
    }

    @GetMapping("/reconciliation/latest")
    @Operation(summary = "Get latest reconciliation run results")
    @ApiResponse(responseCode = "200", description = "Latest reconciliation run retrieved")
    @ApiResponse(responseCode = "404", description = "NO_RECONCILIATION_RUN: No reconciliation has run yet")
    public ResponseEntity<ReconciliationResponse> getLatest() {
        ReconciliationRun run = reconciliationService.getLatestRun()
                .orElseThrow(NoReconciliationRunException::new);
        return ResponseEntity.ok(ReconciliationResponse.from(run));
    }

    @PostMapping("/reconciliation/run")
    @Operation(summary = "Trigger a manual synchronous reconciliation audit run")
    @Parameter(name = "X-Admin-Token", in = ParameterIn.HEADER, required = true, description = "Secret administrative token matching configured wallet.admin-token")
    @ApiResponse(responseCode = "200", description = "Reconciliation audit completed successfully")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Missing or invalid X-Admin-Token")
    @ApiResponse(responseCode = "403", description = "ADMIN_DISABLED: Admin token is not configured on the server")
    @ApiResponse(responseCode = "409", description = "RECONCILIATION_IN_PROGRESS: Another reconciliation audit is currently running")
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
