package com.revpay.controller;

import com.revpay.model.dto.ApiResponse;
import com.revpay.model.dto.JwtResponse;
import com.revpay.model.dto.LoginRequest;
import com.revpay.model.dto.SignupRequest;
import com.revpay.security.JwtUtils;
import com.revpay.security.UserDetailsImpl;
import com.revpay.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuthControllerTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticateUserSuccess() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test@revpay.com");
        loginRequest.setPassword("password");

        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);

        Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PERSONAL"));

        // FIXED: Added 'true' for the isActive flag we introduced earlier
        UserDetailsImpl userDetails = new UserDetailsImpl(1L, "test@revpay.com", "password", true, authorities);

        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(jwtUtils.generateTokenFromUsername("test@revpay.com")).thenReturn("mock-jwt-token");

        ResponseEntity<ApiResponse<JwtResponse>> response = authController.authenticateUser(loginRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("mock-jwt-token", response.getBody().getData().getToken());
    }

    @Test
    void registerUserSuccess() {
        SignupRequest signupRequest = new SignupRequest();
        signupRequest.setEmail("new@revpay.com");

        doNothing().when(authService).registerUser(any(SignupRequest.class));

        ResponseEntity<ApiResponse<String>> response = authController.registerUser(signupRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("User registered successfully!", response.getBody().getMessage());
    }
}