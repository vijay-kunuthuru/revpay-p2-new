package com.revpay.controller;

import com.revpay.exception.ResourceNotFoundException;
import com.revpay.model.dto.ApiResponse;
import com.revpay.model.entity.Notification;
import com.revpay.model.entity.User;
import com.revpay.repository.UserRepository;
import com.revpay.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()") // Secures endpoints for all logged-in users (User, Business, Admin)
@Tag(name = "Notifications", description = "Endpoints for retrieving and managing user inbox notifications and system alerts")
public class NotificationController {

    private final NotificationService service;
    private final UserRepository userRepository;

    // --- DTOs ---
    public record NotificationDTO(Long id, String message, String type, boolean isRead, LocalDateTime createdAt) {}

    // Helper method to extract the full user entity using the injected Authentication object
    private User getAuthenticatedUser(Authentication auth) {
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found in database"));
    }

    // --- ENDPOINTS ---

    @GetMapping
    @Operation(summary = "Get user notifications", description = "Retrieves a paginated list of the authenticated user's notifications, sorted by most recent.")
    public ResponseEntity<ApiResponse<Page<NotificationDTO>>> getMyNotifications(
            Authentication auth,
            @PageableDefault(size = 20) Pageable pageable) {

        User user = getAuthenticatedUser(auth);
        log.debug("Fetching notifications for User ID: {}. Page: {}, Size: {}", user.getUserId(), pageable.getPageNumber(), pageable.getPageSize());

        Page<Notification> notifications = service.getUserNotificationsPaged(user.getUserId(), pageable);

        Page<NotificationDTO> dtos = notifications.map(n -> new NotificationDTO(
                n.getId(),
                n.getMessage(),
                n.getType(),
                n.isRead(),
                n.getCreatedAt()
        ));

        return ResponseEntity.ok(ApiResponse.success(dtos, "Notifications retrieved successfully"));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark notification as read", description = "Updates a specific notification's status to read.")
    public ResponseEntity<ApiResponse<String>> markAsRead(
            @Parameter(description = "ID of the notification to mark as read") @PathVariable Long id) {

        log.info("Marking Notification ID: {} as read", id);

        service.markAsRead(id);

        return ResponseEntity.ok(ApiResponse.success(null, "Notification marked as read"));
    }

    @PostMapping("/test")
    @Operation(summary = "Generate test notification", description = "Creates a sample system notification for the authenticated user to test delivery.")
    public ResponseEntity<ApiResponse<String>> testNotification(Authentication auth) {

        User user = getAuthenticatedUser(auth);
        log.info("Generating test notification for User ID: {}", user.getUserId());

        service.createNotification(
                user.getUserId(),
                "Test notification from system for: " + user.getFullName(),
                "SYSTEM"
        );

        return ResponseEntity.ok(ApiResponse.success(null, "Test notification created successfully"));
    }
}