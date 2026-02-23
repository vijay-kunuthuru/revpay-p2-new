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

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Secures the entire controller
@Tag(name = "Admin Operations", description = "Endpoints for platform administration, auditing, and oversight")
public class AdminController {

    private final InvoiceService invoiceService;
    private final BusinessProfileRepository businessProfileRepository;

    // --- DTOs ---
    public record InvoiceDTO(Long id, Long businessId, String customerName, String customerEmail, BigDecimal totalAmount, LocalDate dueDate, String status) {}
    public record BusinessProfileDTO(Long profileId, Long userId, String businessName, String businessType, String taxId, String address) {}

    // --- ENDPOINTS ---

    @GetMapping("/invoices")
    @Operation(summary = "Get all platform invoices", description = "Retrieves a paginated list of all invoices generated across the RevPay platform.")
    public ResponseEntity<ApiResponse<Page<InvoiceDTO>>> getAllSystemInvoices(@PageableDefault(size = 10) Pageable pageable) {
        log.debug("Admin requested all system invoices. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());

        Page<Invoice> invoices = invoiceService.getAllInvoicesPaged(pageable);
        Page<InvoiceDTO> dtos = invoices.map(i -> new InvoiceDTO(
                i.getId(),
                i.getBusinessProfile().getProfileId(),
                i.getCustomerName(),
                i.getCustomerEmail(),
                i.getTotalAmount(),
                i.getDueDate(),
                i.getStatus().name()
        ));

        return ResponseEntity.ok(ApiResponse.success(dtos, "Invoices retrieved successfully"));
    }

    @GetMapping("/businesses")
    @Operation(summary = "Get all business profiles", description = "Retrieves a paginated list of all registered business profiles.")
    public ResponseEntity<ApiResponse<Page<BusinessProfileDTO>>> getAllBusinessProfiles(@PageableDefault(size = 10) Pageable pageable) {
        log.debug("Admin requested all business profiles. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());

        Page<BusinessProfile> profiles = businessProfileRepository.findAll(pageable);
        Page<BusinessProfileDTO> dtos = profiles.map(p -> new BusinessProfileDTO(
                p.getProfileId(),
                p.getUser().getUserId(),
                p.getBusinessName(),
                p.getBusinessType(),
                p.getTaxId(),
                p.getAddress()
        ));

        return ResponseEntity.ok(ApiResponse.success(dtos, "Business profiles retrieved successfully"));
    }

    @PostMapping("/businesses/{id}/verify")
    @Operation(summary = "Verify a business", description = "Marks a business profile as verified after manual administrative review.")
    public ResponseEntity<ApiResponse<String>> verifyBusiness(@PathVariable Long id) {
        log.info("Admin initiating verification for business profile ID: {}", id);

        // Replaced generic RuntimeException with our specific ResourceNotFoundException
        BusinessProfile profile = businessProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Business profile not found with ID: " + id));

        // Note: Assuming you will add a verification status field to BusinessProfile later.
        // For example:
        // profile.setVerified(true);
        // businessProfileRepository.save(profile);

        log.info("Business profile ID: {} verified successfully.", id);
        return ResponseEntity.ok(ApiResponse.success("Business account verified successfully.", "Verification complete"));
    }
}