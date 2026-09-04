package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.dto.CreateGroupRequest;
import com.settlegraph.Artifacts.dto.GroupInviteResponse;
import com.settlegraph.Artifacts.dto.GroupMemberResponse;
import com.settlegraph.Artifacts.entity.AuditLog;
import com.settlegraph.Artifacts.entity.Group;
import com.settlegraph.Artifacts.entity.GroupInvite;
import com.settlegraph.Artifacts.entity.GroupMember;
import com.settlegraph.Artifacts.entity.User;
import com.settlegraph.Artifacts.repository.AuditLogRepository;
import com.settlegraph.Artifacts.repository.GroupInviteRepository;
import com.settlegraph.Artifacts.repository.GroupMemberRepository;
import com.settlegraph.Artifacts.repository.GroupRepository;
import com.settlegraph.Artifacts.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupInviteRepository groupInviteRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public GroupService(GroupRepository groupRepository,
                         GroupMemberRepository groupMemberRepository,
                         GroupInviteRepository groupInviteRepository,
                         AuditLogRepository auditLogRepository,
                         UserRepository userRepository,
                         NotificationService notificationService) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupInviteRepository = groupInviteRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public Group createGroup(CreateGroupRequest request, Long creatorUserId) {
        Group group = new Group(request.getName(), creatorUserId);
        group = groupRepository.save(group);

        // The creator is always a member.
        groupMemberRepository.save(new GroupMember(creatorUserId, group.getId()));

        if (request.getMemberUserIds() != null) {
            for (Long memberId : request.getMemberUserIds()) {
                if (!memberId.equals(creatorUserId)) {
                    groupMemberRepository.save(new GroupMember(memberId, group.getId()));
                }
            }
        }

        auditLogRepository.save(new AuditLog(group.getId(), creatorUserId,
                "Created group \"" + group.getName() + "\""));

        return group;
    }

    public List<Group> getGroupsForUser(Long userId) {
        return groupRepository.findAllForUser(userId);
    }

    public Group getGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
    }

    public List<GroupMember> getMembers(Long groupId) {
        return groupMemberRepository.findByIdGroupId(groupId);
    }

    public List<GroupMemberResponse> getMembersWithDetails(Long groupId) {
        List<Long> userIds = groupMemberRepository.findByIdGroupId(groupId).stream()
                .map(m -> m.getId().getUserId())
                .toList();
        return userRepository.findAllById(userIds).stream()
                .map(u -> new GroupMemberResponse(u.getId(), u.getUsername(), u.getName(), u.getEmail()))
                .collect(Collectors.toList());
    }

    @Transactional
    public GroupInviteResponse inviteMember(Long groupId, Long inviterUserId, String username) {
        if (!groupMemberRepository.existsById(new GroupMember.GroupMemberId(inviterUserId, groupId))) {
            throw new IllegalArgumentException("Only members of this group can invite others");
        }

        User invitee = userRepository.findByUsername(username.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("No user found with that username"));

        if (invitee.getId().equals(inviterUserId)) {
            throw new IllegalArgumentException("You can't invite yourself");
        }
        if (groupMemberRepository.existsById(new GroupMember.GroupMemberId(invitee.getId(), groupId))) {
            throw new IllegalArgumentException("This person is already in the group");
        }
        if (groupInviteRepository.existsByGroupIdAndInvitedUserIdAndStatus(groupId, invitee.getId(), GroupInvite.Status.PENDING)) {
            throw new IllegalArgumentException("An invite is already pending for this person");
        }

        GroupInvite invite = groupInviteRepository.save(new GroupInvite(groupId, invitee.getId(), inviterUserId));

        auditLogRepository.save(new AuditLog(groupId, inviterUserId,
                "Invited " + invitee.getName() + " to the group"));

        // Tell the invited user they've been invited. They're not a group member
        // yet, so this is a personal notification to them specifically — not part
        // of any group-wide fan-out.
        notificationService.create(invitee.getId(),
                "You've been invited to join \"" + getGroup(groupId).getName() + "\"");

        return toResponse(invite);
    }

    public List<GroupInviteResponse> getInvitesForGroup(Long groupId) {
        return groupInviteRepository.findByGroupIdOrderByCreatedAtDesc(groupId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<GroupInviteResponse> getMyPendingInvites(Long userId) {
        return groupInviteRepository.findByInvitedUserIdAndStatusOrderByCreatedAtDesc(userId, GroupInvite.Status.PENDING).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public GroupInviteResponse respondToInvite(Long inviteId, Long respondingUserId, boolean accept) {
        GroupInvite invite = groupInviteRepository.findById(inviteId)
                .orElseThrow(() -> new IllegalArgumentException("Invite not found"));

        if (!invite.getInvitedUserId().equals(respondingUserId)) {
            throw new IllegalArgumentException("This invite isn't yours to respond to");
        }
        if (invite.getStatus() != GroupInvite.Status.PENDING) {
            throw new IllegalArgumentException("This invite has already been responded to");
        }

        if (accept) {
            groupMemberRepository.save(new GroupMember(respondingUserId, invite.getGroupId()));
            invite.accept();
            User user = userRepository.findById(respondingUserId).orElseThrow();
            auditLogRepository.save(new AuditLog(invite.getGroupId(), respondingUserId,
                    user.getName() + " joined the group"));
        } else {
            invite.decline();
        }

        return toResponse(groupInviteRepository.save(invite));
    }

    private GroupInviteResponse toResponse(GroupInvite invite) {
        Group group = getGroup(invite.getGroupId());
        User invitedUser = userRepository.findById(invite.getInvitedUserId()).orElseThrow();
        User invitedByUser = userRepository.findById(invite.getInvitedBy()).orElseThrow();
        return new GroupInviteResponse(
                invite.getId(), group.getId(), group.getName(),
                invitedUser.getId(), invitedUser.getUsername(), invitedUser.getName(),
                invitedByUser.getId(), invitedByUser.getName(),
                invite.getStatus(), invite.getCreatedAt());
    }
}
