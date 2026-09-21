package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.entity.AuditLog;
import com.settlegraph.Artifacts.entity.Expense;
import com.settlegraph.Artifacts.entity.ExpenseSplit;
import com.settlegraph.Artifacts.entity.GroupMember;
import com.settlegraph.Artifacts.entity.RecurrenceFrequency;
import com.settlegraph.Artifacts.repository.AuditLogRepository;
import com.settlegraph.Artifacts.repository.ExpenseRepository;
import com.settlegraph.Artifacts.repository.ExpenseSplitRepository;
import com.settlegraph.Artifacts.repository.GroupMemberRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

/**
 * Unit tests for {@link RecurringExpenseService}, covering the idempotency
 * guarantee: calling {@code generateDueRecurrences()} twice in a row must
 * only ever create one occurrence for the current period.
 */
class RecurringExpenseServiceTest {

    private static final Long TEMPLATE_ID = 1L;
    private static final Long GROUP_ID = 8L;
    private static final Long PAID_BY = 14L;
    private static final Long OTHER_MEMBER = 20L;

    private ExpenseRepository expenseRepository;
    private ExpenseSplitRepository expenseSplitRepository;
    private AuditLogRepository auditLogRepository;
    private GroupMemberRepository groupMemberRepository;
    private NotificationService notificationService;
    private RecurringExpenseService recurringExpenseService;

    private Expense template;
    private String currentPeriod;

    @BeforeEach
    void setUp() {
        expenseRepository = mock(ExpenseRepository.class);
        expenseSplitRepository = mock(ExpenseSplitRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        groupMemberRepository = mock(GroupMemberRepository.class);
        notificationService = mock(NotificationService.class);

        // A mocked transaction manager: TransactionTemplate still runs the
        // callback and rethrows its exceptions, it just doesn't touch a DB.
        recurringExpenseService = new RecurringExpenseService(
                expenseRepository, expenseSplitRepository, auditLogRepository,
                groupMemberRepository, notificationService,
                mock(PlatformTransactionManager.class));

        template = mock(Expense.class);
        when(template.getId()).thenReturn(TEMPLATE_ID);
        when(template.getGroupId()).thenReturn(GROUP_ID);
        when(template.getPaidBy()).thenReturn(PAID_BY);
        when(template.getAmount()).thenReturn(new BigDecimal("500.00"));
        when(template.getCurrency()).thenReturn("INR");
        when(template.getCategory()).thenReturn("Rent");
        when(template.getDescription()).thenReturn("Monthly rent");
        when(template.getRecurrenceFrequency()).thenReturn(RecurrenceFrequency.MONTHLY);
        // Anchored on the 1st of last month: always due today, and its own
        // period never collides with the current one.
        when(template.getExpenseDate()).thenReturn(LocalDateTime.now().minusMonths(1).withDayOfMonth(1));

        when(expenseRepository.findByRecurringTrue()).thenReturn(List.of(template));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        currentPeriod = YearMonth.now().toString();

        when(expenseSplitRepository.findByIdExpenseId(TEMPLATE_ID))
                .thenReturn(List.of(new ExpenseSplit(TEMPLATE_ID, OTHER_MEMBER, new BigDecimal("500.00"))));

        when(groupMemberRepository.findByIdGroupId(GROUP_ID))
                .thenReturn(List.of(new GroupMember(OTHER_MEMBER, GROUP_ID)));
    }

    @Test
    void generateDueRecurrences_createsOccurrence_whenDueAndNotYetGenerated() {
        when(expenseRepository.existsByRecurringSourceIdAndRecurrencePeriod(TEMPLATE_ID, currentPeriod))
                .thenReturn(false);

        recurringExpenseService.generateDueRecurrences();

        verify(expenseRepository, times(1)).save(any(Expense.class));
        verify(expenseSplitRepository, times(1)).save(any(ExpenseSplit.class));
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
        verify(notificationService, times(1)).create(eq(OTHER_MEMBER), anyString());
    }

    @Test
    void generateDueRecurrences_calledTwiceInARow_createsOnlyOneOccurrence() {
        // First call: not generated yet. Second call: the exists-check now
        // finds the row the first call committed — exactly what happens on
        // a real double-trigger against the DB unique index.
        when(expenseRepository.existsByRecurringSourceIdAndRecurrencePeriod(TEMPLATE_ID, currentPeriod))
                .thenReturn(false, true);

        recurringExpenseService.generateDueRecurrences();
        recurringExpenseService.generateDueRecurrences();

        verify(expenseRepository, times(1)).save(any(Expense.class));
        verify(expenseSplitRepository, times(1)).save(any(ExpenseSplit.class));
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
        verify(notificationService, times(1)).create(eq(OTHER_MEMBER), anyString());
    }

    @Test
    void generateDueRecurrences_skipsTemplate_whenCurrentPeriodMatchesAnchorPeriod() {
        when(template.getExpenseDate()).thenReturn(LocalDateTime.now().withDayOfMonth(1));

        recurringExpenseService.generateDueRecurrences();

        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void generateDueRecurrences_skipsTemplate_whenNotYetDueThisMonth() {
        LocalDateTime today = LocalDateTime.now();
        Assumptions.assumeTrue(today.getDayOfMonth() < 28,
                "anchor day 28 wouldn't be 'not due yet' this late in the month");

        when(template.getExpenseDate()).thenReturn(today.minusMonths(1).withDayOfMonth(28));

        recurringExpenseService.generateDueRecurrences();

        verify(expenseRepository, never()).save(any(Expense.class));
    }

    /**
     * Regression: templates used to share one transaction, so a constraint
     * violation on one template rolled back (and aborted) every other
     * template's occurrence in the same run. Now each template is isolated:
     * the broken one is skipped, the healthy one still gets its occurrence.
     */
    @Test
    void generateDueRecurrences_constraintViolationOnOneTemplate_stillGeneratesTheOtherTemplate() {
        final Long BROKEN_TEMPLATE_ID = 2L;
        Expense brokenTemplate = mock(Expense.class);
        when(brokenTemplate.getId()).thenReturn(BROKEN_TEMPLATE_ID);
        when(brokenTemplate.getGroupId()).thenReturn(GROUP_ID);
        when(brokenTemplate.getPaidBy()).thenReturn(PAID_BY);
        when(brokenTemplate.getAmount()).thenReturn(new BigDecimal("99.00"));
        when(brokenTemplate.getCurrency()).thenReturn("INR");
        when(brokenTemplate.getCategory()).thenReturn("Rent");
        when(brokenTemplate.getDescription()).thenReturn("Broken template");
        when(brokenTemplate.getRecurrenceFrequency()).thenReturn(RecurrenceFrequency.MONTHLY);
        when(brokenTemplate.getExpenseDate()).thenReturn(LocalDateTime.now().minusMonths(1).withDayOfMonth(1));

        // Broken template iterates FIRST, so with the old shared-transaction
        // code the healthy template after it would never have been reached.
        when(expenseRepository.findByRecurringTrue()).thenReturn(List.of(brokenTemplate, template));
        when(expenseRepository.existsByRecurringSourceIdAndRecurrencePeriod(any(), eq(currentPeriod)))
                .thenReturn(false);
        // Saving the broken template's occurrence hits the unique index.
        doThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"))
                .when(expenseRepository)
                .save(argThat(e -> e != null && BROKEN_TEMPLATE_ID.equals(e.getRecurringSourceId())));

        // Must not propagate — the scheduler would otherwise stop dead here.
        recurringExpenseService.generateDueRecurrences();

        // Healthy template's occurrence was still written, with everything that goes with it.
        ArgumentCaptor<Expense> saved = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository, times(2)).save(saved.capture());
        assertTrue(saved.getAllValues().stream()
                .anyMatch(e -> TEMPLATE_ID.equals(e.getRecurringSourceId())
                        && currentPeriod.equals(e.getRecurrencePeriod())),
                "healthy template's occurrence should have been saved");
        verify(expenseSplitRepository, times(1)).save(any(ExpenseSplit.class));
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
        verify(notificationService, times(1)).create(eq(OTHER_MEMBER), anyString());

        // And nothing downstream of the broken template's failed insert ran.
        verify(expenseSplitRepository, never()).findByIdExpenseId(BROKEN_TEMPLATE_ID);
        assertEquals(1, saved.getAllValues().stream()
                .filter(e -> BROKEN_TEMPLATE_ID.equals(e.getRecurringSourceId())).count(),
                "broken template was attempted exactly once, not retried");
    }
}
