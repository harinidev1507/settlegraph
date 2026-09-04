package com.settlegraph.Artifacts.security;

import com.settlegraph.Artifacts.entity.GroupMember;
import com.settlegraph.Artifacts.exception.ForbiddenException;
import com.settlegraph.Artifacts.repository.GroupMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GroupAccessGuard#requireMember}: it throws
 * ForbiddenException (→ HTTP 403) for a user who is not in the group, and
 * returns quietly for a real member.
 *
 * The membership repository is mocked — no database or Spring context.
 */
class GroupAccessGuardTest {

    private static final Long GROUP = 42L;
    private static final Long MEMBER = 1L;
    private static final Long NON_MEMBER = 99L;

    private GroupAccessGuard groupAccessGuard;

    @BeforeEach
    void setUp() {
        GroupMemberRepository groupMemberRepository = mock(GroupMemberRepository.class);
        groupAccessGuard = new GroupAccessGuard(groupMemberRepository);

        when(groupMemberRepository.existsById(new GroupMember.GroupMemberId(MEMBER, GROUP)))
                .thenReturn(true);
        when(groupMemberRepository.existsById(new GroupMember.GroupMemberId(NON_MEMBER, GROUP)))
                .thenReturn(false);
    }

    @Test
    void requireMember_throwsForNonMember() {
        assertThrows(ForbiddenException.class,
                () -> groupAccessGuard.requireMember(GROUP, NON_MEMBER));
    }

    @Test
    void requireMember_passesSilentlyForRealMember() {
        assertDoesNotThrow(() -> groupAccessGuard.requireMember(GROUP, MEMBER));
    }
}
