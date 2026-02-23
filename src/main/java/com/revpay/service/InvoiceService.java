package com.revpay.service;

import com.revpay.exception.ResourceNotFoundException;
import com.revpay.exception.UnauthorizedException;
import com.revpay.model.entity.BusinessProfile;
import com.revpay.model.entity.Invoice;
import com.revpay.repository.BusinessProfileRepository;
import com.revpay.repository.InvoiceRepository;
import com.revpay.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final BusinessProfileRepository businessProfileRepository;

    // ==============================
    // ADMIN METHODS
    // ==============================

    @Transactional(readOnly = true)
    public List<Invoice> getAllInvoices() {
        validateAdminAccess();
        log.info("ADMIN_ACTION | Fetching all platform invoices");
        return invoiceRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<Invoice> getAllInvoicesPaged(Pageable pageable) {
        validateAdminAccess();
        log.info("ADMIN_ACTION | Fetching paginated invoices");
        return invoiceRepository.findAll(pageable);
    }

    // ==============================
    // CREATE INVOICE
    // ==============================

    @Transactional
    public Invoice createInvoice(Long profileId, Invoice invoice) {
        // Validation now returns the profile, preventing a redundant database hit
        BusinessProfile business = getAndValidateBusinessProfile(profileId);

        invoice.setBusinessProfile(business);

        if (invoice.getStatus() == null) {
            invoice.setStatus(Invoice.InvoiceStatus.DRAFT);
        }

        Invoice savedInvoice = invoiceRepository.save(invoice);

        log.info("INVOICE_CREATED | ID: {} | Business Profile ID: {}", savedInvoice.getId(), profileId);
        return savedInvoice;
    }

    // ==============================
    // GET BUSINESS INVOICES
    // ==============================

    @Transactional(readOnly = true)
    public List<Invoice> getAllInvoicesByBusiness(Long profileId) {
        getAndValidateBusinessProfile(profileId); // Validates ownership
        return invoiceRepository.findByBusinessProfile_ProfileId(profileId);
    }

    @Transactional(readOnly = true)
    public Page<Invoice> getAllInvoicesByBusiness(Long profileId, Pageable pageable) {
        getAndValidateBusinessProfile(profileId); // Validates ownership
        return invoiceRepository.findByBusinessProfile_ProfileId(profileId, pageable);
    }

    // ==============================
    // MARK AS PAID
    // ==============================

    @Transactional
    public void markAsPaid(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        Long profileId = invoice.getBusinessProfile().getProfileId();

        // Ensure the person marking it paid actually owns the business
        getAndValidateBusinessProfile(profileId);

        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            log.warn("INVOICE_IDEMPOTENCY | Invoice {} is already marked as PAID", invoiceId);
            return;
        }

        invoice.setStatus(Invoice.InvoiceStatus.PAID);
        invoiceRepository.save(invoice);

        log.info("INVOICE_STATUS_UPDATE | Invoice {} marked as PAID", invoiceId);
    }

    // ==============================
    // SECURITY HELPERS
    // ==============================

    private void validateAdminAccess() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Unauthorized access. Session invalid.");
        }

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            log.warn("UNAUTHORIZED_ACCESS | Non-admin user attempted an admin operation.");
            throw new UnauthorizedException("Admin privileges are required for this action.");
        }
    }

    /**
     * Validates that the logged-in user owns the business profile, and returns it to avoid double-querying.
     */
    private BusinessProfile getAndValidateBusinessProfile(Long profileId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Unauthorized access. Session invalid.");
        }

        BusinessProfile profile = businessProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Business profile not found"));

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        // Admins can bypass ownership checks
        if (isAdmin) {
            return profile;
        }

        Object principal = auth.getPrincipal();
        if (!(principal instanceof UserDetailsImpl userDetails)) {
            throw new UnauthorizedException("Invalid authentication principal structure.");
        }

        Long loggedInUserId = userDetails.getUserId();
        Long ownerUserId = profile.getUser().getUserId();

        if (!loggedInUserId.equals(ownerUserId)) {
            log.warn("UNAUTHORIZED_ACCESS | User {} attempted to access Business Profile {}", loggedInUserId, profileId);
            throw new UnauthorizedException("Access denied. You do not own this business profile.");
        }

        return profile;
    }
}