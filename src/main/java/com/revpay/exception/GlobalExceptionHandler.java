package com.revpay.exception;

import com.revpay.model.dto.ApiResponse;
import com.revpay.model.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleUserExists(UserAlreadyExistsException ex) {

        ErrorResponse error =
                new ErrorResponse("REG_ERR_01", ex.getMessage());

        return new ResponseEntity<>(ApiResponse.error(error, "Registration Failed"), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleWalletRuntime(RuntimeException ex) {

        String code = "WALLET_ERR";

        if (ex.getMessage() != null) {

            if (ex.getMessage().contains("PIN")) {

                code = "AUTH_ERR_02";

            } else if (ex.getMessage().contains("balance")) {

                code = "WALLET_ERR_01";

            } else if (ex.getMessage().contains("limit")) {

                code = "LIMIT_ERR_01";
            }
        }

        ErrorResponse error =
                new ErrorResponse(code, ex.getMessage());

        return new ResponseEntity<>(ApiResponse.error(error, "Operation Failed"), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleGeneral(Exception ex,
                                                                    HttpServletRequest request)
            throws Exception {

        String path = request.getRequestURI();

        if (path.contains("/v3/api-docs")
                || path.contains("/swagger-ui")) {

            throw ex;
        }

        ErrorResponse error =
                new ErrorResponse("SYS_ERR", ex.getMessage());

        return new ResponseEntity<>(ApiResponse.error(error, "Internal Server Error"),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}