package com.revpay.exception;

import com.revpay.model.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserExists(UserAlreadyExistsException ex) {
        ErrorResponse error = new ErrorResponse("REG_ERR_01", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // --- WALLET MODULE EXCEPTIONS ---
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleWalletRuntime(RuntimeException ex) {
        String code = "WALLET_ERR";
        String message = ex.getMessage();

        if (message != null) {
            // Authentication errors
            if (message.contains("PIN")) {
                code = "AUTH_ERR_02";
            }
            // Balance errors
            else if (message.contains("balance") || message.contains("Balance")) {
                code = "WALLET_ERR_01";
            }
            // Limit errors
            else if (message.contains("limit") || message.contains("limit of ₹50,000")) {
                code = "LIMIT_ERR_01";
            }
            // Card errors
            else if (message.contains("card") || message.contains("Card")) {
                if (message.contains("already linked")) {
                    code = "CARD_ERR_01"; // Duplicate card
                } else if (message.contains("not found")) {
                    code = "CARD_ERR_02"; // Card not found
                } else if (message.contains("authorized")) {
                    code = "CARD_ERR_03"; // Not authorized to modify card
                } else {
                    code = "CARD_ERR_04"; // Other card errors
                }
            }
            // Money request errors
            else if (message.contains("request") || message.contains("Request")) {
                if (message.contains("pending")) {
                    code = "REQ_ERR_01"; // Request not pending
                } else if (message.contains("not found")) {
                    code = "REQ_ERR_02"; // Request not found
                } else if (message.contains("authorized")) {
                    code = "REQ_ERR_03"; // Not authorized
                } else {
                    code = "REQ_ERR_04"; // Other request errors
                }
            }
            // User errors
            else if (message.contains("User not found") || message.contains("user not found")) {
                code = "USER_ERR_01";
            }
            // Receiver errors
            else if (message.contains("Receiver not found") || message.contains("receiver not found")) {
                code = "USER_ERR_02";
            }
            // Wallet errors
            else if (message.contains("Wallet not found") || message.contains("wallet not found")) {
                code = "WALLET_ERR_02";
            }
            // Invoice errors
            else if (message.contains("Invoice") || message.contains("invoice")) {
                if (message.contains("not found")) {
                    code = "INV_ERR_01"; // Invoice not found
                } else {
                    code = "INV_ERR_02"; // Other invoice errors
                }
            }
        }

        ErrorResponse error = new ErrorResponse(code, message);
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request)
            throws Exception {

        String path = request.getRequestURI();


        if (path.contains("/v3/api-docs") || path.contains("/swagger-ui")) {
            throw ex;
        }

        ErrorResponse error = new ErrorResponse("SYS_ERR", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}