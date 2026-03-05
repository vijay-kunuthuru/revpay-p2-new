package com.revpay.exception;

import com.revpay.model.dto.ApiResponse;
import com.revpay.model.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
    }

    @Test
    @DisplayName("Should handle InsufficientBalanceException with 402 status")
    void testHandleInsufficientBalance() {
        InsufficientBalanceException ex = new InsufficientBalanceException("Balance too low");

        ResponseEntity<ApiResponse<ErrorResponse>> response = handler.handleInsufficientBalance(ex, request);

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        assertEquals("WAL_001", response.getBody().getData().getErrorCode());
        assertEquals("/api/test", response.getBody().getData().getPath());
    }

    @Test
    @DisplayName("Should handle ResourceNotFoundException with 404 status")
    void testHandleNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User not found");

        ResponseEntity<ApiResponse<ErrorResponse>> response = handler.handleNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("RES_404", response.getBody().getData().getErrorCode());
    }

    @Test
    @DisplayName("Should parse RuntimeException message for Wallet Limit codes")
    void testHandleWalletRuntimeLimit() {
        // Trigger the 'limit' keyword check in your handler
        RuntimeException ex = new RuntimeException("The daily transfer limit has been reached");

        ResponseEntity<ApiResponse<ErrorResponse>> response = handler.handleWalletRuntime(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("LIM_001", response.getBody().getData().getErrorCode());
    }

    @Test
    @DisplayName("Should handle General Exception with 500 status")
    void testHandleGeneral() {
        Exception ex = new Exception("Unexpected IO error");

        ResponseEntity<ApiResponse<ErrorResponse>> response = handler.handleGeneral(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("SYS_500", response.getBody().getData().getErrorCode());
    }

    @Test
    @DisplayName("Should handle Validation Failures and extract field errors")
    void testHandleValidationExceptions() {
        // Mocking the complex MethodArgumentNotValidException
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        FieldError fieldError = new FieldError("user", "email", "Email is required");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ApiResponse<ErrorResponse>> response = handler.handleValidationExceptions(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, String> details = (Map<String, String>) response.getBody().getData().getDetails();
        assertTrue(details.containsKey("email"));
        assertEquals("Email is required", details.get("email"));
    }
}