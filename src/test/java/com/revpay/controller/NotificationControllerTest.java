package com.revpay.controller;

import com.revpay.model.entity.Notification;
import com.revpay.model.entity.User;
import com.revpay.repository.IdempotencyRepository;
import com.revpay.repository.UserRepository;
import com.revpay.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

// Import the required security classes to mock the Principal
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = NotificationController.class)
@AutoConfigureMockMvc(addFilters = false) // Bypass security filters for this specific controller test
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService service;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private IdempotencyRepository idempotencyRepository;

    private Authentication mockAuth;

    @BeforeEach
    void setup() {
        // Create a mock Authentication object to pass into the controller methods
        mockAuth = new UsernamePasswordAuthenticationToken("test@revpay.com", "password");
    }

    @Test
    @WithMockUser(username = "test@revpay.com")
    void getMyNotifications_shouldReturnNotificationList() throws Exception {
        User mockUser = new User();
        mockUser.setUserId(1L);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(mockUser));

        Page<Notification> emptyPage = new PageImpl<>(new ArrayList<>());

        when(service.getUserNotificationsPaged(anyLong(), any(Pageable.class))).thenReturn(emptyPage);

        // FIXED: Added .principal(mockAuth) to ensure the controller method argument is populated
        mockMvc.perform(get("/api/v1/notifications").principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(service).getUserNotificationsPaged(anyLong(), any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "test@revpay.com")
    void markAsRead_shouldReturnSuccessMessage() throws Exception {
        doNothing().when(service).markAsRead(anyLong());

        mockMvc.perform(put("/api/v1/notifications/10/read").principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Notification marked as read"));

        verify(service).markAsRead(eq(10L));
    }

    @Test
    @WithMockUser(username = "test@revpay.com")
    void testNotification_shouldCreateNotification() throws Exception {
        User mockUser = new User();
        mockUser.setUserId(5L);
        mockUser.setFullName("Test User");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(mockUser));

        doNothing().when(service).createNotification(anyLong(), anyString(), anyString());

        // FIXED: Added .principal(mockAuth)
        mockMvc.perform(post("/api/v1/notifications/test").principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(service).createNotification(eq(5L), anyString(), eq("SYSTEM"));
    }
}