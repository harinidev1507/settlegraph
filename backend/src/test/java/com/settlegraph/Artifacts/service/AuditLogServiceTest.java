package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.entity.AuditLog;
import com.settlegraph.Artifacts.exception.ForbiddenException;
import com.settlegraph.Artifacts.repository.AuditLogRepository;
import com.settlegraph.Artifacts.security.GroupAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The audit log is group data: members only, and a non-member is rejected
 * before the log is read. Repository and guard mocked — no Spring context.
 */
class AuditLogServiceTest {

    private static final Long GROUP = 8L;
    private static final Long MEMBER = 5L;
    private static final Long OUTSIDER = 99L;

    private AuditLogRepository auditLogRepository;
    private GroupAccessGuard groupAccessGuard;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        groupAccessGuard = mock(GroupAccessGuard.class);
        doThrow(new ForbiddenException("You are not a member of this group"))
                .when(groupAccessGuard).requireMember(GROUP, OUTSIDER);
        auditLogService = new AuditLogService(auditLogRepository, groupAccessGuard);
    }

    @Test
    void getForGroup_byNonMember_isRejectedBeforeTheLogIsRead() {
        assertThrows(ForbiddenException.class, () -> auditLogService.getForGroup(GROUP, OUTSIDER));
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void getForGroup_byMember_returnsThatGroupsEntries_newestFirst() {
        List<AuditLog> entries = List.of(new AuditLog(GROUP, MEMBER, "second"), new AuditLog(GROUP, MEMBER, "first"));
        when(auditLogRepository.findByGroupIdOrderByCreatedAtDesc(GROUP)).thenReturn(entries);

        assertSame(entries, auditLogService.getForGroup(GROUP, MEMBER));

        verify(groupAccessGuard).requireMember(GROUP, MEMBER);
        verify(auditLogRepository).findByGroupIdOrderByCreatedAtDesc(GROUP);
    }
}
