package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.dto.CreateExpenseRequest;
import com.settlegraph.Artifacts.entity.Expense;
import com.settlegraph.Artifacts.entity.GroupMember;
import com.settlegraph.Artifacts.repository.AuditLogRepository;
import com.settlegraph.Artifacts.repository.ExpenseRepository;
import com.settlegraph.Artifacts.repository.ExpenseSplitRepository;
import com.settlegraph.Artifacts.repository.GroupMemberRepository;
import com.settlegraph.Artifacts.security.GroupAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExpenseService#addExpense}:
 *   - the EQUAL-split contract: an EQUAL split must be given an explicit
 *     participant list, never a silent fall-back to current group membership
 *   - the notification fan-out: adding an expense notifies every OTHER member
 *     of the group, and never the person who added it
 *
 * Repositories and NotificationService are mocked — no database or Spring context.
 */
class ExpenseServiceTest {

    private static final Long GROUP = 42L;
    private static final Long ACTOR = 1L;   // adds the expense
    private static final Long OTHER_A = 2L; // other member
    private static final Long OTHER_B = 3L; // other member

    private GroupMemberRepository groupMemberRepository;
    private NotificationService notificationService;
    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
        ExpenseSplitRepository expenseSplitRepository = mock(ExpenseSplitRepository.class);
        groupMemberRepository = mock(GroupMemberRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        GroupAccessGuard groupAccessGuard = mock(GroupAccessGuard.class);
        notificationService = mock(NotificationService.class);

        expenseService = new ExpenseService(expenseRepository, expenseSplitRepository,
                groupMemberRepository, auditLogRepository, groupAccessGuard, notificationService);

        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void equalSplitWithNoParticipantIds_throws_andDoesNotFallBackToGroupMembership() {
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setGroupId(GROUP);
        request.setAmount(new BigDecimal("100.00"));
        request.setDescription("Dinner");
        request.setSplitType("EQUAL");
        request.setParticipantIds(null); // the whole point of the test

        assertThrows(IllegalArgumentException.class,
                () -> expenseService.addExpense(request, ACTOR));

        // It must not have reached for the group's current membership as a fallback.
        verify(groupMemberRepository, never()).findByIdGroupId(any());
    }

    @Test
    void addingExpenseInThreePersonGroup_notifiesTheTwoOtherMembers_neverTheActor() {
        // A real 3-person group: the actor plus two others.
        when(groupMemberRepository.findByIdGroupId(GROUP)).thenReturn(List.of(
                new GroupMember(ACTOR, GROUP),
                new GroupMember(OTHER_A, GROUP),
                new GroupMember(OTHER_B, GROUP)));

        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setGroupId(GROUP);
        request.setAmount(new BigDecimal("90.00"));
        request.setDescription("Groceries");
        request.setSplitType("EQUAL");
        request.setParticipantIds(List.of(ACTOR, OTHER_A, OTHER_B)); // 30 each

        expenseService.addExpense(request, ACTOR);

        // Exactly the two other members get notified.
        verify(notificationService).create(eq(OTHER_A), anyString());
        verify(notificationService).create(eq(OTHER_B), anyString());
        // Total of two notifications, and NEVER one addressed to the actor.
        verify(notificationService, times(2)).create(any(), anyString());
        verify(notificationService, never()).create(eq(ACTOR), anyString());
    }
}
