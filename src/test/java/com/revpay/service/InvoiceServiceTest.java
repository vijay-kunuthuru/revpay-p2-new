package com.revpay.service;

import com.revpay.model.entity.BusinessProfile;
import com.revpay.model.entity.Invoice;
import com.revpay.model.entity.User;
import com.revpay.model.entity.Role;
import com.revpay.repository.BusinessProfileRepository;
import com.revpay.repository.InvoiceRepository;
import com.revpay.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class InvoiceServiceTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private BusinessProfileRepository businessProfileRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private UserRepository userRepository;

    private BusinessProfile testProfile;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("business_owner@revpay.com");
        user.setFullName("Test Business Owner");
        user.setPhoneNumber("9999988888");
        user.setRole(Role.BUSINESS);

        // These fields are required by your database schema to not be null
        user.setPasswordHash("dummy_hash");
        user.setTransactionPinHash("pin_hash");
        user.setSecurityQuestion("Question?");
        user.setSecurityAnswerHash("answer_hash");

        user = userRepository.save(user);

        testProfile = new BusinessProfile();
        testProfile.setUser(user);
        testProfile.setBusinessName("Test RevPay Business");
        testProfile = businessProfileRepository.save(testProfile);
    }

    @Test
    void testCreateInvoice_SetsDraftStatus() {
        Invoice invoice = new Invoice();
        invoice.setCustomerName("Test Customer");
        invoice.setCustomerEmail("test@example.com");
        invoice.setTotalAmount(new BigDecimal("100.00"));

        Invoice savedInvoice = invoiceService.createInvoice(testProfile.getProfileId(), invoice);

        assertNotNull(savedInvoice.getId());
        assertEquals(Invoice.InvoiceStatus.DRAFT, savedInvoice.getStatus());
        assertEquals(testProfile.getProfileId(), savedInvoice.getBusinessProfile().getProfileId());
    }

    @Test
    void testGetAllInvoicesByBusiness() {
        Invoice invoice = new Invoice();
        invoice.setCustomerName("Test Customer");
        invoice.setCustomerEmail("test@example.com");
        invoice.setTotalAmount(new BigDecimal("100.00"));

        invoiceService.createInvoice(testProfile.getProfileId(), invoice);

        List<Invoice> result = invoiceService.getAllInvoicesByBusiness(testProfile.getProfileId());

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    void testMarkAsPaid_UpdatesStatus() {
        Invoice invoice = new Invoice();
        invoice.setCustomerName("Test Customer");
        invoice.setCustomerEmail("test@example.com");
        invoice.setTotalAmount(new BigDecimal("100.00"));

        Invoice savedInvoice = invoiceService.createInvoice(testProfile.getProfileId(), invoice);

        invoiceService.markAsPaid(savedInvoice.getId());

        Invoice updatedInvoice = invoiceRepository.findById(savedInvoice.getId()).get();
        assertEquals(Invoice.InvoiceStatus.PAID, updatedInvoice.getStatus());
    }

    @Test
    void testCreateInvoice_ThrowsExceptionWhenBusinessNotFound() {
        assertThrows(RuntimeException.class, () -> {
            invoiceService.createInvoice(999L, new Invoice());
        });
    }
}