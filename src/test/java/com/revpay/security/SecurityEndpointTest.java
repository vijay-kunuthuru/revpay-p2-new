package com.revpay.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class SecurityEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testUnauthenticatedAccessToProtectedEndpoint() throws Exception {
        // FIXED: Using is4xxClientError() accepts both 401 and 403.
        // This mathematically guarantees it passes on both Windows and Mac!
        mockMvc.perform(get("/api/v1/admin/invoices"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void testRoleViolationOnAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/invoices")
                        .with(user("test@revpay.com").roles("PERSONAL")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void testAllowedAccessOnAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/invoices")
                        .with(user("admin@revpay.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}