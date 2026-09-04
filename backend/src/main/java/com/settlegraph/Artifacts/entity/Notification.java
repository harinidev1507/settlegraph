package com.settlegraph.Artifacts.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String message;

    @Column(name = "is_read")
    private boolean read = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Notification() {}

    public Notification(Long userId, String message) {
        this.userId = userId;
        this.message = message;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getMessage() { return message; }
    public boolean isRead() { return read; }
    public void markRead() { this.read = true; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
