package com.revpay.service;

import com.revpay.exception.ResourceNotFoundException;
import com.revpay.model.entity.Role;
import com.revpay.model.entity.Transaction;
import com.revpay.model.entity.User;
import com.revpay.repository.BusinessProfileRepository;
import com.revpay.repository.TransactionRepository;
import com.revpay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final BusinessProfileRepository businessProfileRepository;
    private final com.revpay.repository.WalletRepository walletRepository;

    // --- USER MANAGEMENT ---

    @Transactional(readOnly = true)
    public Page<User> getAllUsers(Pageable pageable) {
        log.info("Admin fetching all users (Page: {}, Size: {})", pageable.getPageNumber(), pageable.getPageSize());
        return userRepository.findAll(pageable);
    }

    @Transactional
    public User updateUserStatus(Long userId, boolean isActive) {
        log.info("Admin changing status of user {} to {}", userId, isActive ? "ACTIVE" : "INACTIVE");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        // Let's protect the ADMIN role from being deactivated by another admin or
        // themselves accidentally via the UI.
        if (user.getRole() == Role.ADMIN) {
            throw new IllegalStateException("Cannot change the status of an ADMIN user via this endpoint.");
        }

        user.setActive(isActive);
        return userRepository.save(user);
    }

    // --- TRANSACTION MONITORING ---

    @Transactional(readOnly = true)
    public Page<Transaction> getAllPlatformTransactions(Pageable pageable) {
        log.info("Admin fetching the global transaction ledger (Page: {}, Size: {})", pageable.getPageNumber(),
                pageable.getPageSize());
        return transactionRepository.findAll(pageable);
    }

    // --- SYSTEM ANALYTICS ---

    @Transactional(readOnly = true)
    public Map<String, Object> getSystemAnalytics() {
        log.info("Admin requested platform-wide analytics");

        long totalUsers = userRepository.count();
        long activeBusinesses = businessProfileRepository.count();
        long totalTransactions = transactionRepository.count();

        // Calculate total transaction volume (sum of all COMPLETED SEND and PAYMENT
        // transactions).
        // For simplicity and performance, we'll let JPA handle it if possible, but an
        // explicit query is better for large datasets.
        // For now, we will aggregate locally for MVP or provide an estimate if the DB
        // is too large.
        // Given we don't have a specific JPQL query for volume in TransactionRepository
        // right now,
        // we will fetch all completed transactions. IN A REAL PROD ENVIRONMENT, this
        // should be a DB SUM query.

        // This is a simplified volume calculation for the MVP Dashboard
        BigDecimal estimatedVolume = transactionRepository.findAll().stream()
                .filter(t -> t.getStatus() == Transaction.TransactionStatus.COMPLETED)
                .filter(t -> t.getType() == Transaction.TransactionType.SEND
                        || t.getType() == Transaction.TransactionType.INVOICE_PAYMENT)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate Admin Wallet Balance
        BigDecimal adminWalletBalance = BigDecimal.ZERO;
        User adminUser = userRepository.findByRole(Role.ADMIN).stream().findFirst().orElse(null);
        if (adminUser != null) {
            com.revpay.model.entity.Wallet adminWallet = adminUser.getWallet();
            // If the LAZY relationship isn't initialized or not available directly, we fetch via repository
            if (adminWallet != null) {
                adminWalletBalance = adminWallet.getBalance();
            } else {
                adminWalletBalance = walletRepository.findByUser(adminUser)
                    .map(com.revpay.model.entity.Wallet::getBalance)
                    .orElse(BigDecimal.ZERO);
            }
        }
        
        Map<String, Object> analytics = new HashMap<>();
        analytics.put("totalUsers", totalUsers);
        analytics.put("activeBusinesses", activeBusinesses);
        analytics.put("totalTransactions", totalTransactions);
        analytics.put("totalVolume", estimatedVolume);
        analytics.put("adminWalletBalance", adminWalletBalance);

        return analytics;
    }
}
