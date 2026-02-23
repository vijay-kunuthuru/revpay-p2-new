package com.revpay.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthenticationEntryPointTest {

    private JwtAuthenticationEntryPoint entryPoint;

    @BeforeEach
    void setUp() {
        // Create the mapper and register the JavaTimeModule to handle LocalDateTime serialization
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        entryPoint = new JwtAuthenticationEntryPoint(mapper);
    }

    @Test
    void commence() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException authException = new AuthenticationException("Unauthorized access") {};

        entryPoint.commence(request, response, authException);

        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());

        String contentAsString = response.getContentAsString();

        // Asserting against our new enterprise ApiResponse standard
        assertTrue(contentAsString.contains("AUTH_401"));
        assertTrue(contentAsString.contains("Unauthorized Access"));
        assertTrue(contentAsString.contains("/api/test"));
    }
}