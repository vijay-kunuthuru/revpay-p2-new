package com.revpay.controller;

import com.revpay.exception.ResourceNotFoundException;
import com.revpay.model.dto.ApiResponse;
import com.revpay.model.entity.Transaction;
import com.revpay.model.entity.User;
import com.revpay.repository.UserRepository;
import com.revpay.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER', 'BUSINESS')") // Global security constraint
@Tag(name = "Transaction & Money Requests", description = "Endpoints for viewing transaction history and managing peer-to-peer money requests")
public class TransactionController {

    private final WalletService walletService;
    private final UserRepository userRepository;

    // --- DTOs ---
    public record TransactionDTO(Long transactionId, Long senderId, Long receiverId, BigDecimal amount, String type, String status, String description, LocalDateTime timestamp, String transactionRef) {}

    // Input DTOs to hide sensitive data from URLs
    public record MoneyRequestDTO(
            @NotBlank(message = "Target email is required") @Email(message = "Invalid email format") String targetEmail,
            @NotNull(message = "Amount is required") @Positive(message = "Amount must be greater than zero") BigDecimal amount) {}

    public record AcceptRequestDTO(
            @NotBlank(message = "Transaction PIN is required") String pin) {}

    // --- HELPER METHODS ---

    private User getAuthenticatedUser(Authentication auth) {
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found in database"));
    }

    private TransactionDTO mapToDTO(Transaction t) {
        return new TransactionDTO(
                t.getTransactionId(),
                t.getSender() != null ? t.getSender().getUserId() : null,
                t.getReceiver() != null ? t.getReceiver().getUserId() : null,
                t.getAmount(),
                t.getType() != null ? t.getType().name() : null,
                t.getStatus() != null ? t.getStatus().name() : null,
                t.getDescription(),
                t.getTimestamp(),
                t.getTransactionRef()
        );
    }

    // --- ENDPOINTS ---

    @GetMapping("/history")
    @Operation(summary = "Get transaction history", description = "Retrieves a paginated ledger of all incoming and outgoing transactions for the authenticated user.")
    public ResponseEntity<ApiResponse<Page<TransactionDTO>>> getHistory(
            @Parameter(description = "Filter by transaction type (e.g., SEND, REQUEST, FUND)") @RequestParam(required = false) String type,
            Authentication auth,
            @PageableDefault(size = 20) Pageable pageable) {

        User user = getAuthenticatedUser(auth);
        log.debug("Fetching transaction history for User ID: {}. Type filter: {}", user.getUserId(), type);

        Page<Transaction> transactions = walletService.getMyHistoryPaged(user, type, pageable);
        Page<TransactionDTO> dtos = transactions.map(this::mapToDTO);

        return ResponseEntity.ok(ApiResponse.success(dtos, "Transaction history retrieved successfully"));
    }

    @PostMapping("/request")
    @Operation(summary = "Request money", description = "Sends a pending peer-to-peer money request to another user via their email address.")
    public ResponseEntity<ApiResponse<TransactionDTO>> requestMoney(
            @Valid @RequestBody MoneyRequestDTO request,
            Authentication auth) {

        User user = getAuthenticatedUser(auth);
        log.info("User ID: {} is requesting {} from {}", user.getUserId(), request.amount(), request.targetEmail());

        Transaction t = walletService.requestMoney(user.getUserId(), request.targetEmail(), request.amount());

        return ResponseEntity.ok(ApiResponse.success(mapToDTO(t), "Money requested successfully"));
    }

    @PostMapping("/request/{id}/accept")
    @Operation(
            summary = "Accept money request",
            description = "Fulfills a pending money request. Deducts funds from the current user and transfers them to the requester. Requires transaction PIN verification.",
            parameters = {
                    @Parameter(in = ParameterIn.HEADER, name = "Idempotency-Key", description = "Optional unique key to prevent duplicate processing", example = "uuid-1234")
            }
    )
    public ResponseEntity<ApiResponse<TransactionDTO>> accept(
            @Parameter(description = "ID of the pending transaction request") @PathVariable Long id,
            @Valid @RequestBody AcceptRequestDTO request,
            Authentication auth) {

        User user = getAuthenticatedUser(auth);
        log.info("User ID: {} is attempting to accept and pay Money Request ID: {}", user.getUserId(), id);

        // Uses the secure DTO payload instead of URL parameters for the PIN
        Transaction t = walletService.acceptRequest(id, request.pin());

        log.info("Money Request ID: {} was successfully paid by User ID: {}", id, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(mapToDTO(t), "Money request accepted successfully"));
    }
}