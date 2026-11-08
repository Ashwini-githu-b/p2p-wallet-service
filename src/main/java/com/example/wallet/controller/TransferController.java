package com.example.wallet.controller;

import com.example.wallet.dto.OperationResult;
import com.example.wallet.dto.TransactionResponse;
import com.example.wallet.dto.TransferCommand;
import com.example.wallet.dto.TransferRequest;
import com.example.wallet.exception.InvalidIdempotencyKeyException;
import com.example.wallet.exception.MissingIdempotencyKeyException;
import com.example.wallet.service.TransferFacade;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private final TransferFacade transferFacade;

    public TransferController(TransferFacade transferFacade) {
        this.transferFacade = transferFacade;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> transfer(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        String key = validateIdempotencyKey(idempotencyKey);
        TransferCommand cmd = new TransferCommand(
                request.fromUserId(),
                request.toUserId(),
                request.amountPaise(),
                key
        );

        OperationResult result = transferFacade.transfer(cmd);
        if (result.replayed()) {
            return ResponseEntity.ok()
                    .header("Idempotent-Replay", "true")
                    .body(result.body());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(result.body());
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

