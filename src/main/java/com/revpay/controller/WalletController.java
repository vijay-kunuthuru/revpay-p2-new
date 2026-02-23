package com.revpay.controller;

import com.revpay.exception.ResourceNotFoundException;
import com.revpay.model.dto.*;
import com.revpay.model.entity.*;
import com.revpay.repository.UserRepository;
import com.revpay.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Wallet & Ledger", description = "Complete management of the digital wallet, from money movement to financial reporting")
public class WalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;

    // --- INTERNAL DTOS (Scoped for Controller Cleanliness & Security) ---
    public record TransactionDTO(Long transactionId, Long senderId, Long receiverId, BigDecimal amount, String type, String status, String description, LocalDateTime timestamp, String transactionRef) {}
    public record PaymentMethodDTO(Long id, String partialCardNumber, String expiryDate, String billingAddress, boolean isDefault, String cardType) {}
    public record SimpleAmountRequest(@NotNull @Positive(message = "Amount must be positive") BigDecimal amount, String description) {}
    public record PinPayload(@NotBlank(message = "PIN is required") String pin) {}
    public record MoneyRequestPayload(@NotBlank String targetEmail, @NotNull @Positive BigDecimal amount) {}

    // SECURE DTO FOR CARDS (Prevents Mass Assignment)
    public record CardPayload(
            @NotBlank(message = "Card number is required") String cardNumber,
            @NotBlank(message = "Expiry date is required") @Pattern(regexp = "^(0[1-9]|1[0-2])/?([0-9]{2})$", message = "Format must be MM/YY") String expiryDate,
            @NotBlank(message = "CVV is required") String cvv,
            String billingAddress,
            String cardType
    ) {}

    // --- MAPPING HELPERS ---
    private User getAuthenticatedUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Session invalid or user not found"));
    }

    private TransactionDTO mapTx(Transaction t) {
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

    private PaymentMethodDTO mapCard(PaymentMethod c) {
        String masked = (c.getCardNumber() != null && c.getCardNumber().length() >= 4) ?
                "**** **** **** " + c.getCardNumber().substring(c.getCardNumber().length() - 4) : "INVALID CARD";
        return new PaymentMethodDTO(c.getId(), masked, c.getExpiryDate(), c.getBillingAddress(), c.isDefault(), c.getCardType() != null ? c.getCardType().name() : null);
    }

    // --- 1. CORE MONEY MOVEMENT ---

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<BigDecimal>> getBalance(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(walletService.getBalance(getAuthenticatedUser(auth).getUserId()), "Balance retrieved"));
    }

    @PostMapping("/send")
    @Operation(summary = "P2P Money Transfer", parameters = {@Parameter(in = ParameterIn.HEADER, name = "Idempotency-Key", required = true)})
    public ResponseEntity<ApiResponse<TransactionDTO>> transfer(@Valid @RequestBody TransactionRequest request, Authentication auth) {
        log.info("Money transfer request by user: {}", auth.getName());
        Transaction t = walletService.sendMoney(getAuthenticatedUser(auth).getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(mapTx(t), "Transfer successful"));
    }

    @PostMapping("/add-funds")
    public ResponseEntity<ApiResponse<TransactionDTO>> addFunds(@Valid @RequestBody SimpleAmountRequest req, Authentication auth) {
        Transaction t = walletService.addFunds(getAuthenticatedUser(auth).getUserId(), req.amount(), req.description());
        return ResponseEntity.ok(ApiResponse.success(mapTx(t), "Funds added"));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<ApiResponse<TransactionDTO>> withdraw(@Valid @RequestBody SimpleAmountRequest req, Authentication auth) {
        Transaction t = walletService.withdrawFunds(getAuthenticatedUser(auth).getUserId(), req.amount());
        return ResponseEntity.ok(ApiResponse.success(mapTx(t), "Withdrawal successful"));
    }

    // --- 2. BILL PAY & MONEY REQUESTS ---

    @PostMapping("/pay-invoice")
    public ResponseEntity<ApiResponse<TransactionDTO>> payInvoice(@Valid @RequestBody InvoicePaymentRequest req, Authentication auth) {
        Transaction t = walletService.payInvoice(getAuthenticatedUser(auth).getUserId(), req.getInvoiceId(), req.getTransactionPin());
        return ResponseEntity.ok(ApiResponse.success(mapTx(t), "Invoice paid"));
    }

    @PostMapping("/request")
    public ResponseEntity<ApiResponse<TransactionDTO>> requestMoney(@Valid @RequestBody MoneyRequestPayload req, Authentication auth) {
        Transaction t = walletService.requestMoney(getAuthenticatedUser(auth).getUserId(), req.targetEmail(), req.amount());
        return ResponseEntity.ok(ApiResponse.success(mapTx(t), "Request initiated"));
    }

    @PostMapping("/request/accept/{txnId}")
    public ResponseEntity<ApiResponse<TransactionDTO>> acceptRequest(@PathVariable Long txnId, @Valid @RequestBody PinPayload body) {
        Transaction t = walletService.acceptRequest(txnId, body.pin());
        return ResponseEntity.ok(ApiResponse.success(mapTx(t), "Request fulfilled"));
    }

    @PostMapping("/request/decline/{txnId}")
    public ResponseEntity<ApiResponse<TransactionDTO>> declineRequest(@PathVariable Long txnId, Authentication auth) {
        Transaction t = walletService.declineRequest(getAuthenticatedUser(auth).getUserId(), txnId);
        return ResponseEntity.ok(ApiResponse.success(mapTx(t), "Request declined"));
    }

    @PostMapping("/request/cancel/{txnId}")
    public ResponseEntity<ApiResponse<TransactionDTO>> cancelOutgoingRequest(@PathVariable Long txnId, Authentication auth) {
        Transaction t = walletService.cancelOutgoingRequest(getAuthenticatedUser(auth).getUserId(), txnId);
        return ResponseEntity.ok(ApiResponse.success(mapTx(t), "Request cancelled"));
    }

    @GetMapping("/requests/incoming")
    public ResponseEntity<ApiResponse<Page<TransactionDTO>>> getIncoming(@PageableDefault(size = 10) Pageable p, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(walletService.getIncomingRequestsPaged(getAuthenticatedUser(auth).getUserId(), p).map(this::mapTx), "Incoming requests fetched"));
    }

    @GetMapping("/requests/outgoing")
    public ResponseEntity<ApiResponse<Page<TransactionDTO>>> getOutgoing(@PageableDefault(size = 10) Pageable p, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(walletService.getOutgoingRequestsPaged(getAuthenticatedUser(auth).getUserId(), p).map(this::mapTx), "Outgoing requests fetched"));
    }

    // --- 3. SEARCH & ADVANCED FILTERING ---

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<Page<TransactionDTO>>> getHistory(@RequestParam(required = false) String type, @PageableDefault(size = 20) Pageable p, Authentication auth) {
        User user = getAuthenticatedUser(auth);
        Page<Transaction> res = (type != null && !type.isEmpty()) ? walletService.getMyHistoryPaged(user, type, p) : walletService.getTransactionHistoryPaged(user.getUserId(), p);
        return ResponseEntity.ok(ApiResponse.success(res.map(this::mapTx), "History retrieved"));
    }

    @GetMapping("/transactions/filter")
    public ResponseEntity<ApiResponse<Page<TransactionDTO>>> filter(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) BigDecimal minAmount, @RequestParam(required = false) BigDecimal maxAmount,
            @PageableDefault(size = 20) Pageable p, Authentication auth) {
        Page<Transaction> filtered = walletService.filterTransactionsPaged(getAuthenticatedUser(auth).getUserId(), type, startDate, endDate, status, minAmount, maxAmount, p);
        return ResponseEntity.ok(ApiResponse.success(filtered.map(this::mapTx), "Filtered results retrieved"));
    }

    @PostMapping("/transactions/filter")
    public ResponseEntity<ApiResponse<Page<TransactionDTO>>> filterUsingDTO(@RequestBody TransactionFilterDTO filter, @PageableDefault(size = 20) Pageable p, Authentication auth) {
        Page<Transaction> filtered = walletService.filterTransactionsPaged(getAuthenticatedUser(auth).getUserId(), filter.getType(), filter.getStartDate(), filter.getEndDate(), filter.getStatus(), filter.getMinAmount(), filter.getMaxAmount(), p);
        return ResponseEntity.ok(ApiResponse.success(filtered.map(this::mapTx), "Filtered results retrieved"));
    }

    @GetMapping("/transactions/search")
    public ResponseEntity<ApiResponse<Page<TransactionDTO>>> search(@RequestParam String keyword, @PageableDefault(size = 20) Pageable p, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(walletService.searchTransactionsPaged(getAuthenticatedUser(auth).getUserId(), keyword, p).map(this::mapTx), "Search results retrieved"));
    }

    // --- 4. ANALYTICS & REPORT EXPORT ---

    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<WalletAnalyticsDTO>> getAnalytics(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(walletService.getSpendingAnalytics(getAuthenticatedUser(auth)), "Analytics calculated"));
    }

    @GetMapping("/transactions/export/csv")
    public ResponseEntity<String> exportCsv(Authentication auth) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=revpay_report.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(walletService.exportTransactionsToCSV(getAuthenticatedUser(auth).getUserId()));
    }

    @GetMapping("/transactions/export/pdf")
    public ResponseEntity<byte[]> exportPdf(Authentication auth) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=revpay_report.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(walletService.exportTransactionsToPDF(getAuthenticatedUser(auth).getUserId()));
    }

    // --- 5. SAVED PAYMENT METHODS ---

    @GetMapping("/cards")
    public ResponseEntity<ApiResponse<Page<PaymentMethodDTO>>> getCards(@PageableDefault(size = 10) Pageable p, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(walletService.getCardsPaged(getAuthenticatedUser(auth).getUserId(), p).map(this::mapCard), "Saved cards retrieved"));
    }

    @PostMapping("/cards")
    public ResponseEntity<ApiResponse<PaymentMethodDTO>> addCard(@Valid @RequestBody CardPayload payload, Authentication auth) {
        // Map DTO to Entity manually to prevent mass assignment
        PaymentMethod card = new PaymentMethod();
        card.setCardNumber(payload.cardNumber());
        card.setExpiryDate(payload.expiryDate());
        card.setBillingAddress(payload.billingAddress());
        // cardType and CVV can be mapped here based on your entity structure

        PaymentMethod savedCard = walletService.addCard(getAuthenticatedUser(auth).getUserId(), card);
        return ResponseEntity.ok(ApiResponse.success(mapCard(savedCard), "Card linked successfully"));
    }

    @PutMapping("/cards/{cardId}")
    public ResponseEntity<ApiResponse<PaymentMethodDTO>> updateCard(@PathVariable Long cardId, @Valid @RequestBody CardPayload payload, Authentication auth) {
        PaymentMethod card = new PaymentMethod();
        card.setCardNumber(payload.cardNumber());
        card.setExpiryDate(payload.expiryDate());
        card.setBillingAddress(payload.billingAddress());

        PaymentMethod updatedCard = walletService.updateCard(getAuthenticatedUser(auth).getUserId(), cardId, card);
        return ResponseEntity.ok(ApiResponse.success(mapCard(updatedCard), "Card updated successfully"));
    }

    @DeleteMapping("/cards/{cardId}")
    public ResponseEntity<ApiResponse<String>> deleteCard(@PathVariable Long cardId, Authentication auth) {
        walletService.deleteCard(getAuthenticatedUser(auth).getUserId(), cardId);
        return ResponseEntity.ok(ApiResponse.success(null, "Card unlinked successfully"));
    }

    @PostMapping("/cards/default/{cardId}")
    public ResponseEntity<ApiResponse<String>> setDefault(@PathVariable Long cardId, Authentication auth) {
        walletService.setDefaultCard(getAuthenticatedUser(auth).getUserId(), cardId);
        return ResponseEntity.ok(ApiResponse.success(null, "Primary payment method updated"));
    }
}