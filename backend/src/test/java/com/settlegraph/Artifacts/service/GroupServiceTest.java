package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.dto.CreateGroupRequest;
import com.settlegraph.Artifacts.dto.GroupInviteResponse;
import com.settlegraph.Artifacts.entity.Group;
import com.settlegraph.Artifacts.entity.GroupInvite;
import com.settlegraph.Artifacts.entity.GroupMember;
import com.settlegraph.Artifacts.entity.User;
import com.settlegraph.Artifacts.exception.ForbiddenException;
import com.settlegraph.Artifacts.exception.NotFoundException;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Group reads carry member usernames/emails and pending invites, so they are
 * only visible to members. Each test proves a non-member is rejected BEFORE
 * any repository is touched — not just that an error eventually happened.
 * createGroup is also covered: nobody but the creator joins without an invite;
 * and the invite -> accept/decline happy paths: inviting never adds a member,
 * accepting does, declining doesn't, and someone else's invite is a plain 404.
 */
class GroupServiceTest {

    private static final Long GROUP = 8L;
    private static final Long OUTSIDER = 99L;

    private GroupRepository groupRepository;
    private GroupMemberRepository groupMemberRepository;
    private GroupInviteRepository groupInviteRepository;
    private UserRepository userRepository;
    private AuditLogRepository auditLogRepository;
    private NotificationService notificationService;
    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupRepository = mock(GroupRepository.class);
        groupMemberRepository = mock(GroupMemberRepository.class);
        groupInviteRepository = mock(GroupInviteRepository.class);
        userRepository = mock(UserRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        notificationService = mock(NotificationService.class);
        GroupAccessGuard guard = mock(GroupAccessGuard.class);
        doThrow(new ForbiddenException("You are not a member of this group"))
                .when(guard).requireMember(GROUP, OUTSIDER);

        groupService = new GroupService(groupRepository, groupMemberRepository, groupInviteRepository,
                auditLogRepository, userRepository, notificationService, guard);
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

    // ---- invite -> respond: the happy paths ----

    private static final Long INVITER = 5L;
    private static final Long BOB = 6L;
    private static final Long INVITE_ID = 70L;

    /** A User with a database id (the entity has no id setter). */
    private User user(Long id, String username, String name) {
        User u = spy(new User(username, name, username + "@example.com", "hash"));
        doReturn(id).when(u).getId();
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private void groupNamed(String name) {
        when(groupRepository.findById(GROUP)).thenReturn(Optional.of(new Group(name, INVITER)));
    }

    @Test
    void inviteMember_byAMember_savesAPendingInvite_auditsIt_andNotifiesOnlyTheInvitee() {
        groupNamed("Goa Trip");
        user(INVITER, "alice", "Alice");
        User bob = user(BOB, "bob", "Bob");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(groupInviteRepository.save(any(GroupInvite.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupInviteResponse response = groupService.inviteMember(GROUP, INVITER, "  Bob "); // trimmed + lowercased

        ArgumentCaptor<GroupInvite> saved = ArgumentCaptor.forClass(GroupInvite.class);
        verify(groupInviteRepository).save(saved.capture());
        assertEquals(GROUP, saved.getValue().getGroupId());
        assertEquals(BOB, saved.getValue().getInvitedUserId());
        assertEquals(INVITER, saved.getValue().getInvitedBy());
        assertEquals(GroupInvite.Status.PENDING, saved.getValue().getStatus());
        assertEquals(GroupInvite.Status.PENDING, response.getStatus());
        assertEquals("bob", response.getInvitedUsername());
        assertEquals("Alice", response.getInvitedByName());

        // Inviting does NOT make anyone a member — only accepting does.
        verify(groupMemberRepository, never()).save(any());
        verify(auditLogRepository, times(1)).save(any());
        verify(notificationService).create(eq(BOB), argThat(m -> m.contains("Goa Trip")));
        verify(notificationService, times(1)).create(any(), anyString());
    }

    @Test
    void respondToInvite_accept_byTheInvitee_addsTheMembership_marksItAccepted_andAudits() {
        groupNamed("Goa Trip");
        user(INVITER, "alice", "Alice");
        user(BOB, "bob", "Bob");
        GroupInvite invite = new GroupInvite(GROUP, BOB, INVITER);
        when(groupInviteRepository.findByIdAndInvitedUserId(INVITE_ID, BOB)).thenReturn(Optional.of(invite));
        when(groupInviteRepository.save(any(GroupInvite.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupInviteResponse response = groupService.respondToInvite(INVITE_ID, BOB, true);

        ArgumentCaptor<GroupMember> member = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(member.capture());
        assertEquals(BOB, member.getValue().getId().getUserId());
        assertEquals(GROUP, member.getValue().getId().getGroupId());
        assertEquals(GroupInvite.Status.ACCEPTED, invite.getStatus());
        assertNotNull(invite.getRespondedAt());
        assertEquals(GroupInvite.Status.ACCEPTED, response.getStatus());
        verify(groupInviteRepository).save(invite);
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    void respondToInvite_decline_byTheInvitee_marksItDeclined_andAddsNoMembership() {
        groupNamed("Goa Trip");
        user(INVITER, "alice", "Alice");
        user(BOB, "bob", "Bob");
        GroupInvite invite = new GroupInvite(GROUP, BOB, INVITER);
        when(groupInviteRepository.findByIdAndInvitedUserId(INVITE_ID, BOB)).thenReturn(Optional.of(invite));
        when(groupInviteRepository.save(any(GroupInvite.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupInviteResponse response = groupService.respondToInvite(INVITE_ID, BOB, false);

        assertEquals(GroupInvite.Status.DECLINED, invite.getStatus());
        assertNotNull(invite.getRespondedAt());
        assertEquals(GroupInvite.Status.DECLINED, response.getStatus());
        verify(groupMemberRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void respondToInvite_bySomeoneOtherThanTheInvitee_looksExactlyLikeAMissingInvite_andChangesNothing() {
        // Used to be a 400 "isn't yours" after reading the invite — which confirmed it existed.
        Long stranger = 99L;
        GroupInvite invite = new GroupInvite(GROUP, BOB, INVITER);
        when(groupInviteRepository.findByIdAndInvitedUserId(INVITE_ID, BOB)).thenReturn(Optional.of(invite));

        NotFoundException notYours = assertThrows(NotFoundException.class,
                () -> groupService.respondToInvite(INVITE_ID, stranger, true));
        NotFoundException missing = assertThrows(NotFoundException.class,
                () -> groupService.respondToInvite(12345L, stranger, true));

        assertEquals(missing.getMessage(), notYours.getMessage());
        assertEquals(GroupInvite.Status.PENDING, invite.getStatus());
        // Only the invitee-scoped lookup is used — never an unscoped read of the invite.
        verify(groupInviteRepository, never()).findById(any());
        verify(groupInviteRepository, never()).save(any());
        verify(groupMemberRepository, never()).save(any());
        verifyNoInteractions(auditLogRepository);
    }
}
