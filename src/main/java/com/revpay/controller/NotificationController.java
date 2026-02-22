package com.revpay.controller;

import com.revpay.model.entity.Notification;
import com.revpay.model.entity.User;
import com.revpay.repository.UserRepository;
import com.revpay.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {

    @Autowired private NotificationService service;
    @Autowired private UserRepository userRepository;

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }


    @GetMapping
    public ResponseEntity<List<Notification>> getMyNotifications() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(service.getUserNotifications(user.getUserId()));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<String> markAsRead(@PathVariable Long id) {
        service.markAsRead(id);
        return ResponseEntity.ok("Notification marked as read");
    }

    @PostMapping("/test")
    public ResponseEntity<String> testNotification() {
        User user = getAuthenticatedUser();
        service.createNotification(
                user.getUserId(),
                "Test notification from system for: " + user.getFullName(),
                "SYSTEM"
        );
        return ResponseEntity.ok("Test notification created successfully");
    }
}