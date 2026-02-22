package com.revpay.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AdversarialSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testMalformedJwtSignature() throws Exception {
        String tamperedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiS" +
                "WF0IjoxNTE2MjM5MDIyfQ.invalid_signature_here";

        mockMvc.perform(get("/api/v1/wallet/balance")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testSqlInjectionPayloadInAuth() throws Exception {
        String payload = "{\"email\": \"admin@revpay.com' OR '1'='1\", \"password\": \"password\"}";

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testOversizedPayloadAttack() throws Exception {
        StringBuilder largeString = new StringBuilder();
        for(int i=0; i<100000; i++) {
            largeString.append("A");
        }
        String payload = "{\"email\": \"" + largeString + "@revpay.com\", \"password\": \"password\"}";

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void testMissingAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/balance"))
                .andExpect(status().isUnauthorized());
    }
}