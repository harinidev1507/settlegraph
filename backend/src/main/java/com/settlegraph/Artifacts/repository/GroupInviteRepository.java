package com.settlegraph.Artifacts.repository;

import com.settlegraph.Artifacts.entity.GroupInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GroupInviteRepository extends JpaRepository<GroupInvite, Long> {
    List<GroupInvite> findByGroupIdOrderByCreatedAtDesc(Long groupId);
    List<GroupInvite> findByInvitedUserIdAndStatusOrderByCreatedAtDesc(Long invitedUserId, GroupInvite.Status status);
    boolean existsByGroupIdAndInvitedUserIdAndStatus(Long groupId, Long invitedUserId, GroupInvite.Status status);
}
