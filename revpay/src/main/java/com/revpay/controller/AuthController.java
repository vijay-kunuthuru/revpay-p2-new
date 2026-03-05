package com.revpay.controller;

import com.revpay.model.dto.ApiResponse;
import com.revpay.model.dto.ForgotPasswordRequest;
import com.revpay.model.dto.JwtResponse;
import com.revpay.model.dto.LoginRequest;
import com.revpay.model.dto.ResetPasswordRequest;
import com.revpay.model.dto.SignupRequest;
import com.revpay.model.dto.UpdatePasswordRequest;
import com.revpay.security.JwtUtils;
import com.revpay.security.UserDetailsImpl;
import com.revpay.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication Operations", description = "Endpoints for user registration, login, JWT generation, and password management")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final AuthService authService;

    // Helper method to extract the logged-in user's ID
    private Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return userDetails.getUserId();
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user", description = "Validates user credentials and returns a JWT token for subsequent API requests.")
    public ResponseEntity<ApiResponse<JwtResponse>> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("Authentication attempt for email: {}", loginRequest.getEmail());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateTokenFromUsername(loginRequest.getEmail());

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        String role = userDetails.getAuthorities().iterator().next().getAuthority();

        JwtResponse jwtResponse = new JwtResponse(jwt, userDetails.getUserId(), userDetails.getEmail(), role);

        log.info("User {} authenticated successfully. Role: {}", userDetails.getEmail(), role);
        return ResponseEntity.ok(ApiResponse.success(jwtResponse, "Login successful"));
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new RevPay user account. Validates email uniqueness and hashes the password.")
    public ResponseEntity<ApiResponse<String>> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        log.info("Registration attempt for email: {}", signUpRequest.getEmail());

        authService.registerUser(signUpRequest);

        log.info("User {} registered successfully.", signUpRequest.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null, "User registered successfully!"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Get security question", description = "Retrieves the security question registered to the provided email address for password recovery.")
    public ResponseEntity<ApiResponse<String>> getSecurityQuestion(@Valid @RequestBody ForgotPasswordRequest request) {
        log.debug("Security question requested for email: {}", request.getEmail());

        String question = authService.getSecurityQuestion(request.getEmail());

        return ResponseEntity.ok(ApiResponse.success(question, "Security question retrieved successfully"));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset forgotten password", description = "Resets the user's password after successfully validating the security question answer.")
    public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("Password reset attempt for email: {}", request.getEmail());

        authService.resetPassword(request);

        log.info("Password reset successfully for email: {}", request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null, "Password has been reset successfully"));
    }

    @PutMapping("/update-password")
    @Operation(summary = "Update current password", description = "Updates the password for the currently authenticated user. Requires the current password for verification.")
    public ResponseEntity<ApiResponse<String>> updatePassword(@Valid @RequestBody UpdatePasswordRequest request) {
        Long currentUserId = getAuthenticatedUserId();
        log.info("Password update initiated by userId: {}", currentUserId);

        authService.updatePassword(currentUserId, request);

        log.info("Password updated successfully for userId: {}", currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Password updated successfully"));
    }

    @PutMapping("/update-pin")
    @Operation(summary = "Update transaction PIN", description = "Updates the transaction PIN for the currently authenticated user. Requires the current PIN for verification.")
    public ResponseEntity<ApiResponse<String>> updatePin(
            @Valid @RequestBody com.revpay.model.dto.UpdatePinRequest request) {
        Long currentUserId = getAuthenticatedUserId();
        log.info("Transaction PIN update initiated by userId: {}", currentUserId);

        authService.updateTransactionPin(currentUserId, request);

        log.info("Transaction PIN updated successfully for userId: {}", currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Transaction PIN updated successfully"));
    }
}