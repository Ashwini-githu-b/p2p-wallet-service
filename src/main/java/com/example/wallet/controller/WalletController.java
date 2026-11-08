package com.example.wallet.controller;

import com.example.wallet.dto.HistoryItem;
import com.example.wallet.dto.OperationResult;
import com.example.wallet.dto.PageResponse;
import com.example.wallet.dto.TopUpCommand;
import com.example.wallet.dto.TopUpRequest;
import com.example.wallet.dto.TransactionResponse;
import com.example.wallet.dto.WalletResponse;
import com.example.wallet.exception.InvalidIdempotencyKeyException;
import com.example.wallet.exception.MissingIdempotencyKeyException;
import com.example.wallet.service.TransferFacade;
import com.example.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/wallets")
@Tag(name = "Wallets", description = "Wallet balance lookup, top-up operations, and transaction history")
public class WalletController {

    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private final WalletService walletService;
    private final TransferFacade transferFacade;

    public WalletController(WalletService walletService, TransferFacade transferFacade) {
        this.walletService = walletService;
        this.transferFacade = transferFacade;
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get wallet balance and details by user ID")
    @ApiResponse(responseCode = "200", description = "Wallet retrieved successfully")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND: User does not exist")
    public WalletResponse getWallet(@PathVariable UUID userId) {
        return walletService.getWallet(userId);
    }

    @PostMapping("/{userId}/topup")
    @Operation(summary = "Top up wallet balance with double-entry ledger guarantee")
    @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true, description = "Unique idempotency key matching ^[A-Za-z0-9_-]{8,64}$")
    @ApiResponse(responseCode = "201", description = "Top-up applied successfully")
    @ApiResponse(responseCode = "200", description = "Idempotent replay: previously committed top-up returned")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR / INVALID_AMOUNT / MISSING_IDEMPOTENCY_KEY / INVALID_IDEMPOTENCY_KEY")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND: User does not exist")
    @ApiResponse(responseCode = "422", description = "IDEMPOTENCY_KEY_REUSED: Key was previously used with a different payload")
    public ResponseEntity<TransactionResponse> topUp(
            @PathVariable UUID userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TopUpRequest request) {

        String key = validateIdempotencyKey(idempotencyKey);
        TopUpCommand cmd = new TopUpCommand(userId, request.amountPaise(), key);

        OperationResult result = transferFacade.topUp(cmd);
        if (result.replayed()) {
            return ResponseEntity.ok()
                    .header("Idempotent-Replay", "true")
                    .body(result.body());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(result.body());
    }

    @GetMapping("/{userId}/transactions")
    @Operation(summary = "Get paginated, chronological transaction ledger entries for a wallet")
    @ApiResponse(responseCode = "200", description = "Ledger transactions retrieved successfully")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND: User does not exist")
    public PageResponse<HistoryItem> getHistory(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0) {
            throw new IllegalArgumentException("Page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Size must be between 1 and 100");
        }

        return walletService.getHistory(userId, page, size);
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException();
        }
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new InvalidIdempotencyKeyException();
        }
        return idempotencyKey;
    }
}
