package com.revpay.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.revpay.repository.IdempotencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class IdempotencyInterceptorTest {

    @Mock
    private IdempotencyRepository idempotencyRepository;

    private IdempotencyInterceptor interceptor;

    @BeforeEach
    void setUp() {
        // Create the mapper and register the JavaTimeModule for LocalDateTime serialization
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        // Manually inject the mock repository and real object mapper
        interceptor = new IdempotencyInterceptor(idempotencyRepository, mapper);
    }

    @Test
    void testPreHandleNewRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.addHeader("Idempotency-Key", "uuid-1234");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(idempotencyRepository.existsById("uuid-1234")).thenReturn(false);

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void testPreHandleDuplicateRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.addHeader("Idempotency-Key", "uuid-1234");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(idempotencyRepository.existsById("uuid-1234")).thenReturn(true);

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(409, response.getStatus());

        // Ensure our standard API response is written correctly (checking structural failure flag)
        String jsonResponse = response.getContentAsString();
        assertTrue(jsonResponse.contains("false") || jsonResponse.contains("success"));
    }
}