package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.dto.CreateGroupRequest;
import com.settlegraph.Artifacts.entity.Group;
import com.settlegraph.Artifacts.entity.GroupMember;
import com.settlegraph.Artifacts.exception.ForbiddenException;
import com.settlegraph.Artifacts.repository.AuditLogRepository;
import com.settlegraph.Artifacts.repository.GroupInviteRepository;
import com.settlegraph.Artifacts.repository.GroupMemberRepository;
import com.settlegraph.Artifacts.repository.GroupRepository;
import com.settlegraph.Artifacts.repository.UserRepository;
import com.settlegraph.Artifacts.security.GroupAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Group reads carry member usernames/emails and pending invites, so they are
 * only visible to members. Each test proves a non-member is rejected BEFORE
 * any repository is touched — not just that an error eventually happened.
 * createGroup is also covered: nobody but the creator joins without an invite.
 */
class GroupServiceTest {

    private static final Long GROUP = 8L;
    private static final Long OUTSIDER = 99L;

    private GroupRepository groupRepository;
    private GroupMemberRepository groupMemberRepository;
    private GroupInviteRepository groupInviteRepository;
    private UserRepository userRepository;
    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupRepository = mock(GroupRepository.class);
        groupMemberRepository = mock(GroupMemberRepository.class);
        groupInviteRepository = mock(GroupInviteRepository.class);
        userRepository = mock(UserRepository.class);
        GroupAccessGuard guard = mock(GroupAccessGuard.class);
        doThrow(new ForbiddenException("You are not a member of this group"))
                .when(guard).requireMember(GROUP, OUTSIDER);

        groupService = new GroupService(groupRepository, groupMemberRepository, groupInviteRepository,
                mock(AuditLogRepository.class), userRepository, mock(NotificationService.class), guard);
    }

    @Test
    void getGroup_byNonMember_isRejectedBeforeTheGroupIsRead() {
        assertThrows(ForbiddenException.class, () -> groupService.getGroup(GROUP, OUTSIDER));
        verifyNoInteractions(groupRepository);
    }

    @Test
    void getMembers_byNonMember_isRejectedBeforeAnyUserDataIsRead() {
        assertThrows(ForbiddenException.class, () -> groupService.getMembersWithDetails(GROUP, OUTSIDER));
        verifyNoInteractions(groupMemberRepository, userRepository);
    }

    @Test
    void getGroupInvites_byNonMember_isRejectedBeforeInvitesAreRead() {
        assertThrows(ForbiddenException.class, () -> groupService.getInvitesForGroup(GROUP, OUTSIDER));
        verifyNoInteractions(groupInviteRepository);
    }

    @Test
    void inviteMember_byNonMember_isRejectedBeforeAnyLookup() {
        assertThrows(ForbiddenException.class, () -> groupService.inviteMember(GROUP, OUTSIDER, "someone"));
        verifyNoInteractions(userRepository, groupInviteRepository);
    }

    // ---- createGroup: the only way in besides creating is an accepted invite ----

    private static final Long CREATOR = 5L;

    private CreateGroupRequest createRequest(List<Long> memberUserIds) {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Goa Trip");
        request.setMemberUserIds(memberUserIds);
        return request;
    }

    @Test
    void createGroup_withANonexistentMemberUserId_isRejectedAs400_andNothingIsWritten() {
        // Audit bug 3: used to reach the member insert and fail on the FK -> 500.
        assertThrows(IllegalArgumentException.class,
                () -> groupService.createGroup(createRequest(List.of(99_999_999L)), CREATOR));
        verifyNoInteractions(groupRepository, groupMemberRepository, userRepository);
    }

    @Test
    void createGroup_withARealMemberUserId_isRejected_soNoOneIsAddedWithoutAnInvite() {
        // Audit bug 3: used to add user 6 as a member with no invite and no consent.
        assertThrows(IllegalArgumentException.class,
                () -> groupService.createGroup(createRequest(List.of(6L)), CREATOR));
        verifyNoInteractions(groupRepository, groupMemberRepository);
    }

    @Test
    void createGroup_withAnEmptyMemberList_createsTheGroupWithOnlyTheCreatorAsMember() {
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));

        groupService.createGroup(createRequest(List.of()), CREATOR);

        ArgumentCaptor<GroupMember> member = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository, times(1)).save(member.capture());
        assertEquals(CREATOR, member.getValue().getId().getUserId());
    }
}
