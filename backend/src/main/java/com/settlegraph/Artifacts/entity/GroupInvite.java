package com.settlegraph.Artifacts.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_invite")
public class GroupInvite {

    public enum Status { PENDING, ACCEPTED, DECLINED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "invited_user_id", nullable = false)
    private Long invitedUserId;

    @Column(name = "invited_by", nullable = false)
    private Long invitedBy;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    public GroupInvite() {}

    public GroupInvite(Long groupId, Long invitedUserId, Long invitedBy) {
        this.groupId = groupId;
        this.invitedUserId = invitedUserId;
        this.invitedBy = invitedBy;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Long getInvitedUserId() { return invitedUserId; }
    public Long getInvitedBy() { return invitedBy; }
    public Status getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getRespondedAt() { return respondedAt; }

    public void accept() { this.status = Status.ACCEPTED; this.respondedAt = LocalDateTime.now(); }
    public void decline() { this.status = Status.DECLINED; this.respondedAt = LocalDateTime.now(); }
}
