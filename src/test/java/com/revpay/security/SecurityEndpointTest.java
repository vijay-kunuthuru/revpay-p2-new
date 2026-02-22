package com.revpay.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class SecurityEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testUnauthenticatedAccessToProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/wallet/balance"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "PERSONAL")
    void testRoleViolationOnAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/invoices"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testAllowedAccessOnAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/invoices"))
                .andExpect(status().isOk());
    }
}