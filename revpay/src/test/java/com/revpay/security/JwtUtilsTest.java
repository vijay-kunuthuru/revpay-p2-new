package com.revpay.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwtUtils;
    private final String testEmail = "test@revpay.com";

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        // Secret must be at least 256 bits (32 characters) for HS256
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 86400000);
    }

    @Test
    void generateAndValidateToken() {
        String token = jwtUtils.generateTokenFromUsername(testEmail);
        assertNotNull(token);

        boolean isValid = jwtUtils.validateJwtToken(token);
        assertTrue(isValid, "Token should be valid when generated with the same secret");
    }

    @Test
    void extractUsernameFromToken() {
        String token = jwtUtils.generateTokenFromUsername(testEmail);
        String extractedEmail = jwtUtils.getUserNameFromJwtToken(token);

        assertEquals(testEmail, extractedEmail);
    }

    @Test
    void validateTokenFailure() {
        // A token that will fail signature check because it wasn't generated with our secret
        String fakeToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJmYWtlQG1haWwuY29tIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

        boolean isValid = jwtUtils.validateJwtToken(fakeToken);

        assertFalse(isValid, "Validation should return false instead of throwing an exception");
    }
}