package com.revpay.controller;

import com.revpay.model.dto.*;
import com.revpay.model.entity.*;
import com.revpay.repository.UserRepository;
import com.revpay.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    @Autowired private WalletService walletService;
    @Autowired private UserRepository userRepository;

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    // --- 1. CORE OPERATIONS ---

    @GetMapping("/balance")
    public ResponseEntity<BigDecimal> getBalance() {
        return ResponseEntity.ok(walletService.getBalance(getAuthenticatedUser().getUserId()));
    }

    @PostMapping("/send")
    public ResponseEntity<Transaction> transfer(@RequestBody TransactionRequest request) {
        return ResponseEntity.ok(walletService.sendMoney(getAuthenticatedUser().getUserId(), request));
    }

    @PostMapping("/add-funds")
    public ResponseEntity<Transaction> addFunds(@RequestBody Map<String, Object> request) {
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String description = request.getOrDefault("description", "Wallet Deposit").toString();
        return ResponseEntity.ok(walletService.addFunds(getAuthenticatedUser().getUserId(), amount, description));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<Transaction> withdraw(@RequestBody Map<String, Object> request) {
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        return ResponseEntity.ok(walletService.withdrawFunds(getAuthenticatedUser().getUserId(), amount));
    }

    // --- 2. INVOICE & MONEY REQUESTS (UPDATED) ---

    @PostMapping("/pay-invoice")
    public ResponseEntity<Transaction> payInvoice(@RequestBody InvoicePaymentRequest request) {
        return ResponseEntity.ok(walletService.payInvoice(
                getAuthenticatedUser().getUserId(),
                request.getInvoiceId(),
                request.getTransactionPin()
        ));
    }

    @PostMapping("/request")
    public ResponseEntity<Transaction> requestMoney(@RequestBody Map<String, Object> request) {
        String targetEmail = request.get("targetEmail").toString();
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        return ResponseEntity.ok(walletService.requestMoney(getAuthenticatedUser().getUserId(), targetEmail, amount));
    }

    @PostMapping("/request/accept/{txnId}")
    public ResponseEntity<Transaction> acceptRequest(@PathVariable Long txnId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(walletService.acceptRequest(txnId, body.get("pin")));
    }

    // NEW: Decline an incoming money request
    @PostMapping("/request/decline/{txnId}")
    public ResponseEntity<Transaction> declineRequest(@PathVariable Long txnId) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(walletService.declineRequest(user.getUserId(), txnId));
    }

    // NEW: Cancel an outgoing pending request
    @PostMapping("/request/cancel/{txnId}")
    public ResponseEntity<Transaction> cancelOutgoingRequest(@PathVariable Long txnId) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(walletService.cancelOutgoingRequest(user.getUserId(), txnId));
    }

    // NEW: Get all incoming pending requests
    @GetMapping("/requests/incoming")
    public ResponseEntity<List<Transaction>> getIncomingRequests() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(walletService.getIncomingRequests(user.getUserId()));
    }

    // NEW: Get all outgoing pending requests
    @GetMapping("/requests/outgoing")
    public ResponseEntity<List<Transaction>> getOutgoingRequests() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(walletService.getOutgoingRequests(user.getUserId()));
    }

    // --- 3. HISTORY, FILTERING, SEARCH & EXPORT (UPDATED) ---

    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getHistory(@RequestParam(required = false) String type) {
        User user = getAuthenticatedUser();
        if (type != null && !type.isEmpty()) {
            return ResponseEntity.ok(walletService.getMyHistory(user, type));
        }
        return ResponseEntity.ok(walletService.getTransactionHistory(user.getUserId()));
    }

    // Filter transactions with query parameters (GET)
    @GetMapping("/transactions/filter")
    public ResponseEntity<List<Transaction>> filterTransactions(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(walletService.filterTransactions(
                user.getUserId(), type, startDate, endDate, status, minAmount, maxAmount));
    }

    // NEW: Filter transactions using DTO (POST with JSON body) - This uses TransactionFilterDTO!
    @PostMapping("/transactions/filter")
    public ResponseEntity<List<Transaction>> filterTransactionsUsingDTO(@RequestBody TransactionFilterDTO filter) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(walletService.filterTransactions(
                user.getUserId(),
                filter.getType(),
                filter.getStartDate(),
                filter.getEndDate(),
                filter.getStatus(),
                filter.getMinAmount(),
                filter.getMaxAmount()
        ));
    }

    // Search transactions by keyword
    @GetMapping("/transactions/search")
    public ResponseEntity<List<Transaction>> searchTransactions(
            @RequestParam String keyword) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(walletService.searchTransactions(user.getUserId(), keyword));
    }

    // Export transactions to CSV
    @GetMapping("/transactions/export/csv")
    public ResponseEntity<String> exportToCSV() {
        User user = getAuthenticatedUser();
        String csv = walletService.exportTransactionsToCSV(user.getUserId());

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=transactions.csv")
                .header("Content-Type", "text/csv")
                .body(csv);
    }

    // Export transactions to PDF
    @GetMapping("/transactions/export/pdf")
    public ResponseEntity<byte[]> exportToPDF() {
        User user = getAuthenticatedUser();
        byte[] pdf = walletService.exportTransactionsToPDF(user.getUserId());

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=transactions.pdf")
                .header("Content-Type", "application/pdf")
                .body(pdf);
    }

    @GetMapping("/analytics")
    public ResponseEntity<WalletAnalyticsDTO> getAnalytics() {
        return ResponseEntity.ok(walletService.getSpendingAnalytics(getAuthenticatedUser()));
    }

    // --- 4. CARD MANAGEMENT (UPDATED) ---

    @GetMapping("/cards")
    public ResponseEntity<List<PaymentMethod>> getMyCards() {
        return ResponseEntity.ok(walletService.getCards(getAuthenticatedUser().getUserId()));
    }

    @PostMapping("/cards")
    public ResponseEntity<PaymentMethod> addCard(@RequestBody PaymentMethod card) {
        return ResponseEntity.ok(walletService.addCard(getAuthenticatedUser().getUserId(), card));
    }

    // Update card details (expiry, billing address)
    @PutMapping("/cards/{cardId}")
    public ResponseEntity<PaymentMethod> updateCard(
            @PathVariable Long cardId,
            @RequestBody PaymentMethod card) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(walletService.updateCard(user.getUserId(), cardId, card));
    }

    @DeleteMapping("/cards/{cardId}")
    public ResponseEntity<Map<String, String>> deleteCard(@PathVariable Long cardId) {
        walletService.deleteCard(getAuthenticatedUser().getUserId(), cardId);
        return ResponseEntity.ok(Map.of("message", "Card deleted successfully"));
    }

    @PostMapping("/cards/default/{cardId}")
    public ResponseEntity<Map<String, String>> setDefault(@PathVariable Long cardId) {
        walletService.setDefaultCard(getAuthenticatedUser().getUserId(), cardId);
        return ResponseEntity.ok(Map.of("message", "Default card updated"));
    }
}