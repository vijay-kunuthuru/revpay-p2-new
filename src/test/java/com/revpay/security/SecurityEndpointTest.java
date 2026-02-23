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
        // FIXED: Expect 403 Forbidden, matching the custom AccessDeniedHandler response
        mockMvc.perform(get("/api/v1/admin/invoices"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testRoleViolationOnAdminEndpoint() throws Exception {
        // Simulate a Personal user trying to access an Admin route
        mockMvc.perform(get("/api/v1/admin/invoices")
                        .with(user("test@revpay.com").roles("PERSONAL")))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAllowedAccessOnAdminEndpoint() throws Exception {
        // Admin endpoint does not do a DB lookup, making it perfect for an integration test 200 assertion
        mockMvc.perform(get("/api/v1/admin/invoices")
                        .with(user("admin@revpay.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}