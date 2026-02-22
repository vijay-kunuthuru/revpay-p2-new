package com.revpay.config;

import com.revpay.model.entity.IdempotencyKey;
import com.revpay.repository.IdempotencyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class IdempotencyInterceptorTest {

    @Mock
    private IdempotencyRepository idempotencyRepository;

    @InjectMocks
    private IdempotencyInterceptor interceptor;

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
    }
}