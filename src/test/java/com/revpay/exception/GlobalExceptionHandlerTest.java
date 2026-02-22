package com.revpay.exception;

import com.revpay.model.dto.ApiResponse;
import com.revpay.model.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void testHandleWalletRuntimeLimit() {
        RuntimeException ex = new RuntimeException("limit of INR50,000 exceeded");
        ResponseEntity<ApiResponse<ErrorResponse>> response = exceptionHandler.handleWalletRuntime(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("LIMIT_ERR_01", response.getBody().getData().getErrorCode());
    }

    @Test
    void testHandleGeneralException() throws Exception {
        Exception ex = new Exception("Database failure");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/wallet/balance");

        ResponseEntity<ApiResponse<ErrorResponse>> response = exceptionHandler.handleGeneral(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("SYS_ERR", response.getBody().getData().getErrorCode());
    }
}