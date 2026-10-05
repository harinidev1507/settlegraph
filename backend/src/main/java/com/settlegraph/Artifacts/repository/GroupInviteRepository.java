package com.settlegraph.Artifacts.repository;

import com.settlegraph.Artifacts.entity.GroupInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GroupInviteRepository extends JpaRepository<GroupInvite, Long> {
    List<GroupInvite> findByGroupIdOrderByCreatedAtDesc(Long groupId);
    List<GroupInvite> findByInvitedUserIdAndStatusOrderByCreatedAtDesc(Long invitedUserId, GroupInvite.Status status);
    /** Scoped lookup: someone else's invite is indistinguishable from a missing one. */
    Optional<GroupInvite> findByIdAndInvitedUserId(Long id, Long invitedUserId);
    boolean existsByGroupIdAndInvitedUserIdAndStatus(Long groupId, Long invitedUserId, GroupInvite.Status status);
}
