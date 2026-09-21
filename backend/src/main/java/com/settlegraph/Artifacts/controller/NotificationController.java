package com.settlegraph.Artifacts.controller;

import com.settlegraph.Artifacts.entity.Notification;
import com.settlegraph.Artifacts.security.AuthenticatedUser;
import com.settlegraph.Artifacts.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<Notification>> getMine(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(notificationService.getForUser(user.getUserId()));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        notificationService.markRead(id, user.getUserId());
        return ResponseEntity.noContent().build();
    }
}
