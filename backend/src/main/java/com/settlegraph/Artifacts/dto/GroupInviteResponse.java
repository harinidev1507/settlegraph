package com.settlegraph.Artifacts.dto;

import com.settlegraph.Artifacts.entity.GroupInvite;
import java.time.LocalDateTime;

public class GroupInviteResponse {
    private Long id;
    private Long groupId;
    private String groupName;
    private Long invitedUserId;
    private String invitedUsername;
    private String invitedName;
    private Long invitedByUserId;
    private String invitedByName;
    private GroupInvite.Status status;
    private LocalDateTime createdAt;

    public GroupInviteResponse(Long id, Long groupId, String groupName,
                                Long invitedUserId, String invitedUsername, String invitedName,
                                Long invitedByUserId, String invitedByName,
                                GroupInvite.Status status, LocalDateTime createdAt) {
        this.id = id;
        this.groupId = groupId;
        this.groupName = groupName;
        this.invitedUserId = invitedUserId;
        this.invitedUsername = invitedUsername;
        this.invitedName = invitedName;
        this.invitedByUserId = invitedByUserId;
        this.invitedByName = invitedByName;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public String getGroupName() { return groupName; }
    public Long getInvitedUserId() { return invitedUserId; }
    public String getInvitedUsername() { return invitedUsername; }
    public String getInvitedName() { return invitedName; }
    public Long getInvitedByUserId() { return invitedByUserId; }
    public String getInvitedByName() { return invitedByName; }
    public GroupInvite.Status getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
