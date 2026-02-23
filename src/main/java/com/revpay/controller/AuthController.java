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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AuthService authService;

    private Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return userDetails.getUserId();
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtResponse>> authenticateUser(@RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateTokenFromUsername(loginRequest.getEmail());

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        String role = userDetails.getAuthorities().iterator().next().getAuthority();

        JwtResponse jwtResponse = new JwtResponse(jwt, userDetails.getUserId(), userDetails.getEmail(), role);
        return ResponseEntity.ok(ApiResponse.success(jwtResponse, "Login successful"));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> registerUser(@RequestBody SignupRequest signUpRequest) {
        authService.registerUser(signUpRequest);
        return ResponseEntity.ok(ApiResponse.success(null, "User registered successfully!"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> getSecurityQuestion(@RequestBody ForgotPasswordRequest request) {
        String question = authService.getSecurityQuestion(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(question, "Security question retrieved successfully"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password has been reset successfully"));
    }

    @PutMapping("/update-password")
    public ResponseEntity<ApiResponse<String>> updatePassword(@RequestBody UpdatePasswordRequest request) {
        Long currentUserId = getAuthenticatedUserId();
        authService.updatePassword(currentUserId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password updated successfully"));
    }
}