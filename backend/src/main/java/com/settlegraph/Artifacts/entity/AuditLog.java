package com.settlegraph.Artifacts.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "performed_by")
    private Long performedBy;

    @Column(nullable = false)
    private String action;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public AuditLog() {}

    public AuditLog(Long groupId, Long performedBy, String action) {
        this.groupId = groupId;
        this.performedBy = performedBy;
        this.action = action;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Long getPerformedBy() { return performedBy; }
    public String getAction() { return action; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
