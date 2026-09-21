package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.entity.Expense;
import com.settlegraph.Artifacts.exception.ForbiddenException;
import com.settlegraph.Artifacts.repository.ExpenseRepository;
import com.settlegraph.Artifacts.security.GroupAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AnalyticsService}, built on group 8's real, known
 * expense data:
 *   - "ice cream"  1000.00  category "General"  2026-08-29
 *   - "movie"       600.00  category "General"  2026-08-29
 * Those two expenses are split between two members (500/500 and 180/420
 * respectively), but analytics must ignore the splits entirely and sum the
 * expense amount once per expense: 1000 + 600 = 1600 in both aggregations.
 *
 * Repositories and the access guard are mocked — no database or Spring context.
 */
class AnalyticsServiceTest {

    private static final Long GROUP_8 = 8L;
    private static final Long MEMBER = 14L;
    private static final Long NON_MEMBER = 99L;

    private ExpenseRepository expenseRepository;
    private GroupAccessGuard groupAccessGuard;
    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        expenseRepository = mock(ExpenseRepository.class);
        groupAccessGuard = mock(GroupAccessGuard.class);
        analyticsService = new AnalyticsService(expenseRepository, groupAccessGuard);

        Expense iceCream = mock(Expense.class);
        when(iceCream.getAmount()).thenReturn(new BigDecimal("1000.00"));
        when(iceCream.getCategory()).thenReturn("General");
        when(iceCream.getExpenseDate()).thenReturn(LocalDateTime.of(2026, 8, 29, 20, 47, 3));

        Expense movie = mock(Expense.class);
        when(movie.getAmount()).thenReturn(new BigDecimal("600.00"));
        when(movie.getCategory()).thenReturn("General");
        when(movie.getExpenseDate()).thenReturn(LocalDateTime.of(2026, 8, 29, 20, 47, 3));

        when(expenseRepository.findByGroupIdOrderByExpenseDateDesc(GROUP_8))
                .thenReturn(List.of(iceCream, movie));
    }

    @Test
    void spendByCategory_sumsExpenseAmountsPerCategory_notSplitShares() {
        Map<String, BigDecimal> byCategory = analyticsService.spendByCategory(GROUP_8, MEMBER);

        assertEquals(1, byCategory.size());
        assertEquals(0, new BigDecimal("1600.00").compareTo(byCategory.get("General")));
    }

    @Test
    void spendByMonth_sumsExpenseAmountsPerYearMonth_notSplitShares() {
        Map<String, BigDecimal> byMonth = analyticsService.spendByMonth(GROUP_8, MEMBER);

        assertEquals(1, byMonth.size());
        assertEquals(0, new BigDecimal("1600.00").compareTo(byMonth.get("2026-08")));
    }

    @Test
    void spendByCategory_nonMember_isRejectedBeforeTouchingExpenses() {
        doThrow(new ForbiddenException("You are not a member of this group"))
                .when(groupAccessGuard).requireMember(GROUP_8, NON_MEMBER);

        assertThrows(ForbiddenException.class,
                () -> analyticsService.spendByCategory(GROUP_8, NON_MEMBER));
        verifyNoInteractions(expenseRepository);
    }

    @Test
    void spendByMonth_nonMember_isRejectedBeforeTouchingExpenses() {
        doThrow(new ForbiddenException("You are not a member of this group"))
                .when(groupAccessGuard).requireMember(GROUP_8, NON_MEMBER);

        assertThrows(ForbiddenException.class,
                () -> analyticsService.spendByMonth(GROUP_8, NON_MEMBER));
        verifyNoInteractions(expenseRepository);
    }

    @Test
    void bothAggregations_checkMembershipForThatGroup() {
        analyticsService.spendByCategory(GROUP_8, MEMBER);
        analyticsService.spendByMonth(GROUP_8, MEMBER);

        verify(groupAccessGuard, org.mockito.Mockito.times(2)).requireMember(GROUP_8, MEMBER);
    }
}
