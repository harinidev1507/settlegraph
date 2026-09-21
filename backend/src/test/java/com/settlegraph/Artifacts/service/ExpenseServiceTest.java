package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.dto.CreateExpenseRequest;
import com.settlegraph.Artifacts.entity.Expense;
import com.settlegraph.Artifacts.entity.GroupMember;
import com.settlegraph.Artifacts.entity.RecurrenceFrequency;
import com.settlegraph.Artifacts.exception.ForbiddenException;
import com.settlegraph.Artifacts.repository.AuditLogRepository;
import com.settlegraph.Artifacts.repository.ExpenseRepository;
import com.settlegraph.Artifacts.repository.ExpenseSplitRepository;
import com.settlegraph.Artifacts.repository.GroupMemberRepository;
import com.settlegraph.Artifacts.security.GroupAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
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

    private ExpenseRepository expenseRepository;
    private ExpenseSplitRepository expenseSplitRepository;
    private GroupMemberRepository groupMemberRepository;
    private AuditLogRepository auditLogRepository;
    private GroupAccessGuard groupAccessGuard;
    private NotificationService notificationService;
    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        expenseRepository = mock(ExpenseRepository.class);
        expenseSplitRepository = mock(ExpenseSplitRepository.class);
        groupMemberRepository = mock(GroupMemberRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        groupAccessGuard = mock(GroupAccessGuard.class);
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

    private CreateExpenseRequest equalRequest(BigDecimal amount) {
        when(groupMemberRepository.findByIdGroupId(GROUP)).thenReturn(List.of(
                new GroupMember(ACTOR, GROUP), new GroupMember(OTHER_A, GROUP)));
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setGroupId(GROUP);
        request.setAmount(amount);
        request.setDescription("Rent");
        request.setSplitType("EQUAL");
        request.setParticipantIds(List.of(ACTOR, OTHER_A));
        return request;
    }

    // ---- authorization: the actor must be a member of the group ----

    @Test
    void nonMemberAddingExpense_isRejected_beforeAnythingIsReadOrWritten() {
        doThrow(new ForbiddenException("You are not a member of this group"))
                .when(groupAccessGuard).requireMember(GROUP, ACTOR);
        CreateExpenseRequest request = equalRequest(new BigDecimal("100.00"));

        assertThrows(ForbiddenException.class, () -> expenseService.addExpense(request, ACTOR));

        verify(expenseRepository, never()).save(any());
        verify(expenseSplitRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
        verify(notificationService, never()).create(any(), anyString());
        verify(groupMemberRepository, never()).findByIdGroupId(any());
    }

    @Test
    void invalidSplit_isRejected_beforeTheExpenseRowIsWritten() {
        // Used to save the expense first and validate afterwards, relying on
        // the transaction to undo it. Now nothing is written on a bad request.
        CreateExpenseRequest request = equalRequest(new BigDecimal("100.00"));
        request.setSplitType("EXACT");
        request.setSplits(Map.of(ACTOR, new BigDecimal("10.00"), OTHER_A, new BigDecimal("10.00"))); // sums to 20, not 100

        assertThrows(IllegalArgumentException.class, () -> expenseService.addExpense(request, ACTOR));

        verify(expenseRepository, never()).save(any());
        verify(expenseSplitRepository, never()).save(any());
    }

    @Test
    void negativeSplitValue_isRejected() {
        CreateExpenseRequest request = equalRequest(new BigDecimal("100.00"));
        request.setSplitType("EXACT");
        request.setSplits(Map.of(ACTOR, new BigDecimal("150.00"), OTHER_A, new BigDecimal("-50.00"))); // sums to 100!

        assertThrows(IllegalArgumentException.class, () -> expenseService.addExpense(request, ACTOR));
        verify(expenseRepository, never()).save(any());
    }

    // ---- recurring expenses ----

    @Test
    void recurringExpense_isSavedAsAMonthlyTemplate_withSplitsLikeAnyOtherExpense() {
        CreateExpenseRequest request = equalRequest(new BigDecimal("500.00"));
        request.setRecurring(true);
        request.setRecurrenceFrequency(RecurrenceFrequency.MONTHLY);

        expenseService.addExpense(request, ACTOR);

        ArgumentCaptor<Expense> saved = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(saved.capture());
        assertTrue(saved.getValue().isRecurring());
        assertEquals(RecurrenceFrequency.MONTHLY, saved.getValue().getRecurrenceFrequency());
        assertNull(saved.getValue().getRecurringSourceId(), "a template is not an occurrence of anything");
        verify(expenseSplitRepository, times(2)).save(any()); // 250 / 250
    }

    @Test
    void nonRecurringExpense_hasNoFrequency() {
        expenseService.addExpense(equalRequest(new BigDecimal("100.00")), ACTOR);

        ArgumentCaptor<Expense> saved = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(saved.capture());
        assertFalse(saved.getValue().isRecurring());
        assertNull(saved.getValue().getRecurrenceFrequency());
    }

    @Test
    void recurringWithoutFrequency_isRejected_andWritesNothing() {
        CreateExpenseRequest request = equalRequest(new BigDecimal("500.00"));
        request.setRecurring(true);
        request.setRecurrenceFrequency(null);

        assertThrows(IllegalArgumentException.class, () -> expenseService.addExpense(request, ACTOR));
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void frequencyWithoutRecurring_isRejected() {
        CreateExpenseRequest request = equalRequest(new BigDecimal("500.00"));
        request.setRecurring(false);
        request.setRecurrenceFrequency(RecurrenceFrequency.MONTHLY);

        assertThrows(IllegalArgumentException.class, () -> expenseService.addExpense(request, ACTOR));
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void stopRecurring_clearsTheTemplateFlag_andAuditsIt() {
        Expense template = new Expense(GROUP, ACTOR, new BigDecimal("500.00"), "INR", "Rent", "Monthly rent");
        template.setRecurring(true);
        template.setRecurrenceFrequency(RecurrenceFrequency.MONTHLY);
        when(expenseRepository.findById(7L)).thenReturn(Optional.of(template));

        Expense result = expenseService.stopRecurring(7L, OTHER_A);

        assertFalse(result.isRecurring());
        verify(expenseRepository).save(template);
        verify(auditLogRepository).save(any());
    }

    @Test
    void stopRecurring_byNonMember_isRejected_andNothingIsWritten() {
        Expense template = new Expense(GROUP, ACTOR, new BigDecimal("500.00"), "INR", "Rent", "Monthly rent");
        template.setRecurring(true);
        when(expenseRepository.findById(7L)).thenReturn(Optional.of(template));
        doThrow(new ForbiddenException("nope")).when(groupAccessGuard).requireMember(GROUP, 99L);

        assertThrows(ForbiddenException.class, () -> expenseService.stopRecurring(7L, 99L));

        assertTrue(template.isRecurring(), "flag must be untouched");
        verify(expenseRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void stopRecurring_onAnOrdinaryExpense_isRejected() {
        Expense plain = new Expense(GROUP, ACTOR, new BigDecimal("50.00"), "INR", "Food", "Lunch");
        when(expenseRepository.findById(8L)).thenReturn(Optional.of(plain));

        assertThrows(IllegalArgumentException.class, () -> expenseService.stopRecurring(8L, ACTOR));
        verify(expenseRepository, never()).save(any());
    }
}
