package com.revpay.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class AuthServiceTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void testPasswordHashing() {
        String rawPassword = "password123";
        String encoded = passwordEncoder.encode(rawPassword);
        
        // Assert that the encoded password is not plain text
        assertNotEquals(rawPassword, encoded);
        // Verify the password matches
        assertTrue(passwordEncoder.matches(rawPassword, encoded));
    }
}