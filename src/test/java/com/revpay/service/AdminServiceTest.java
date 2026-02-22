package com.revpay.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class AdminServiceTest {

    @Autowired
    private InvoiceService invoiceService;

    @Test
    @WithMockUser(roles = "ADMIN") // Simulates an Admin user login
    void testAdminAccessToAllInvoices() {
        var allInvoices = invoiceService.getAllInvoices();
        assertNotNull(allInvoices);
        // Additional logic to verify admin can see invoices from multiple businesses
    }
}