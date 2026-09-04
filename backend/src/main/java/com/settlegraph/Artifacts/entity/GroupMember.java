package com.settlegraph.Artifacts.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "group_member")
public class GroupMember {

    @EmbeddedId
    private GroupMemberId id;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt = LocalDateTime.now();

    public GroupMember() {}

    public GroupMember(Long userId, Long groupId) {
        this.id = new GroupMemberId(userId, groupId);
    }

    public GroupMemberId getId() { return id; }
    public LocalDateTime getJoinedAt() { return joinedAt; }

    @Embeddable
    public static class GroupMemberId implements Serializable {
        @Column(name = "user_id")
        private Long userId;
        @Column(name = "group_id")
        private Long groupId;

        public GroupMemberId() {}
        public GroupMemberId(Long userId, Long groupId) {
            this.userId = userId;
            this.groupId = groupId;
        }
        public Long getUserId() { return userId; }
        public Long getGroupId() { return groupId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GroupMemberId)) return false;
            GroupMemberId that = (GroupMemberId) o;
            return Objects.equals(userId, that.userId) && Objects.equals(groupId, that.groupId);
        }

        @Override
        public int hashCode() { return Objects.hash(userId, groupId); }
    }
}
