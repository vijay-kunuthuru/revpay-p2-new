package com.revpay.controller;

import com.revpay.exception.ResourceNotFoundException;
import com.revpay.model.dto.ApiResponse;
import com.revpay.model.entity.BusinessProfile;
import com.revpay.model.entity.Invoice;
import com.revpay.repository.BusinessProfileRepository;
import com.revpay.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Secures the entire controller
@Tag(name = "Admin Operations", description = "Endpoints for platform administration, auditing, and oversight")
public class AdminController {

        private final InvoiceService invoiceService;
        private final BusinessProfileRepository businessProfileRepository;
        private final com.revpay.service.AdminService adminService;

        // --- DTOs ---
        public record InvoiceDTO(Long id, Long businessId, String customerName, String customerEmail,
                        BigDecimal totalAmount, LocalDate dueDate, String status) {
        }

        public record BusinessProfileDTO(Long profileId, Long userId, String ownerName, String ownerEmail,
                        String businessName, String businessType,
                        String taxId, String address, boolean verified) {
        }

        public record UserAdminDTO(Long userId, String email, String fullName, String phoneNumber, String role,
                        boolean isActive, boolean isVerified, LocalDateTime createdAt) {
        }

        public record TransactionAdminDTO(Long transactionId, Long senderId, String senderName, String senderEmail,
                        Long receiverId, String receiverName, String receiverEmail, BigDecimal amount,
                        String type, String status, String description, java.time.LocalDateTime timestamp,
                        String transactionRef) {
        }

        public record UserStatusRequest(boolean active) {
        }

        // --- USER MANAGEMENT ---

        @GetMapping("/users")
        @Operation(summary = "Get all users", description = "Retrieves a paginated list of all registered users on the platform.")
        public ResponseEntity<ApiResponse<Page<UserAdminDTO>>> getAllUsers(
                        @PageableDefault(size = 20) Pageable pageable) {
                Page<UserAdminDTO> users = adminService.getAllUsers(pageable)
                                .map(u -> new UserAdminDTO(u.getUserId(), u.getEmail(), u.getFullName(),
                                                u.getPhoneNumber(),
                                                u.getRole().name(), u.isActive(), u.isVerified(), u.getCreatedAt()));
                return ResponseEntity.ok(ApiResponse.success(users, "Users retrieved successfully"));
        }

        @PutMapping("/users/{userId}/status")
        @Operation(summary = "Update User Status", description = "Activates or deactivates a user account, preventing login if deactivated.")
        public ResponseEntity<ApiResponse<String>> updateUserStatus(@PathVariable Long userId,
                        @RequestBody UserStatusRequest request) {
                adminService.updateUserStatus(userId, request.active());
                return ResponseEntity
                                .ok(ApiResponse.success("Status updated",
                                                "User account status has been updated successfully."));
        }

        // --- BUSINESS MANAGEMENT ---

        @GetMapping("/businesses")
        @Operation(summary = "Get all business profiles", description = "Retrieves a paginated list of all registered business profiles.")
        public ResponseEntity<ApiResponse<Page<BusinessProfileDTO>>> getAllBusinessProfiles(
                        @PageableDefault(size = 10) Pageable pageable) {
                log.debug("Admin requested all business profiles. Page: {}, Size: {}", pageable.getPageNumber(),
                                pageable.getPageSize());

                Page<BusinessProfile> profiles = businessProfileRepository.findAll(pageable);
                Page<BusinessProfileDTO> dtos = profiles.map(p -> new BusinessProfileDTO(
                                p.getProfileId(),
                                p.getUser().getUserId(),
                                p.getUser().getFullName(),
                                p.getUser().getEmail(),
                                p.getBusinessName(),
                                p.getBusinessType(),
                                p.getTaxId(),
                                p.getAddress(),
                                p.isVerified()));

                return ResponseEntity.ok(ApiResponse.success(dtos, "Business profiles retrieved successfully"));
        }

        @PostMapping("/businesses/{id}/verify")
        @Operation(summary = "Verify a business", description = "Marks a business profile as verified after manual administrative review.")
        public ResponseEntity<ApiResponse<String>> verifyBusiness(@PathVariable Long id) {
                log.info("Admin initiating verification for business profile ID: {}", id);

                BusinessProfile profile = businessProfileRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Business profile not found with ID: " + id));

                if (profile.isVerified()) {
                        return ResponseEntity.badRequest()
                                        .body(ApiResponse.error("VAL_002", "Business is already verified."));
                }

                profile.setVerified(true);
                businessProfileRepository.save(profile);

                // Activate the user account so they can log in
                adminService.updateUserStatus(profile.getUser().getUserId(), true);

                return ResponseEntity
                                .ok(ApiResponse.success("Verification complete",
                                                "Business account verified successfully."));
        }

        @PutMapping("/businesses/{id}/suspend")
        @Operation(summary = "Suspend a business", description = "Revokes verification status from a business profile.")
        public ResponseEntity<ApiResponse<String>> suspendBusiness(@PathVariable Long id) {
                log.info("Admin suspending business profile ID: {}", id);

                BusinessProfile profile = businessProfileRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Business profile not found with ID: " + id));

                profile.setVerified(false);
                businessProfileRepository.save(profile);

                return ResponseEntity
                                .ok(ApiResponse.success("Suspension complete",
                                                "Business account suspended successfully."));
        }

        // --- SYSTEM LEDGER & INVOICES ---

        @GetMapping("/transactions")
        @Operation(summary = "Get System Ledger", description = "Retrieves all transactions executed across the entire platform.")
        public ResponseEntity<ApiResponse<Page<TransactionAdminDTO>>> getSystemLedger(
                        @PageableDefault(size = 50) Pageable pageable) {
                Page<TransactionAdminDTO> txns = adminService.getAllPlatformTransactions(pageable)
                                .map(t -> new TransactionAdminDTO(
                                                t.getTransactionId(),
                                                t.getSender() != null ? t.getSender().getUserId() : null,
                                                t.getSender() != null ? t.getSender().getFullName() : null,
                                                t.getSender() != null ? t.getSender().getEmail() : null,
                                                t.getReceiver() != null ? t.getReceiver().getUserId() : null,
                                                t.getReceiver() != null ? t.getReceiver().getFullName() : null,
                                                t.getReceiver() != null ? t.getReceiver().getEmail() : null,
                                                t.getAmount(),
                                                t.getType().name(),
                                                t.getStatus().name(),
                                                t.getDescription(),
                                                t.getTimestamp(),
                                                t.getTransactionRef()));
                return ResponseEntity.ok(ApiResponse.success(txns, "System Ledger retrieved"));
        }

        @GetMapping("/invoices")
        @Operation(summary = "Get all platform invoices", description = "Retrieves a paginated list of all invoices generated across the RevPay platform.")
        public ResponseEntity<ApiResponse<Page<InvoiceDTO>>> getAllSystemInvoices(
                        @PageableDefault(size = 10) Pageable pageable) {
                Page<Invoice> invoices = invoiceService.getAllInvoicesPaged(pageable);
                Page<InvoiceDTO> dtos = invoices.map(i -> new InvoiceDTO(
                                i.getId(),
                                i.getBusinessProfile().getProfileId(),
                                i.getCustomerName(),
                                i.getCustomerEmail(),
                                i.getTotalAmount(),
                                i.getDueDate(),
                                i.getStatus().name()));

                return ResponseEntity.ok(ApiResponse.success(dtos, "Invoices retrieved successfully"));
        }

        // --- ANALYTICS ---

        @GetMapping("/analytics")
        @Operation(summary = "System Analytics", description = "Get platform-wide metrics regarding users, businesses, and transaction volume.")
        public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getAnalytics() {
                return ResponseEntity
                                .ok(ApiResponse.success(adminService.getSystemAnalytics(), "System metrics retrieved"));
        }
}