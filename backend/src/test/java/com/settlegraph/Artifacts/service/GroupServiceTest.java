package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.exception.ForbiddenException;
import com.settlegraph.Artifacts.repository.AuditLogRepository;
import com.settlegraph.Artifacts.repository.GroupInviteRepository;
import com.settlegraph.Artifacts.repository.GroupMemberRepository;
import com.settlegraph.Artifacts.repository.GroupRepository;
import com.settlegraph.Artifacts.repository.UserRepository;
import com.settlegraph.Artifacts.security.GroupAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Group reads carry member usernames/emails and pending invites, so they are
 * only visible to members. Each test proves a non-member is rejected BEFORE
 * any repository is touched — not just that an error eventually happened.
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
}
