package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.entity.Expense;
import com.settlegraph.Artifacts.entity.ExpenseSplit;
import com.settlegraph.Artifacts.entity.Settlement;
import com.settlegraph.Artifacts.repository.ExpenseRepository;
import com.settlegraph.Artifacts.repository.ExpenseSplitRepository;
import com.settlegraph.Artifacts.repository.SettlementRepository;
import com.settlegraph.Artifacts.security.GroupAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BalanceService#calculateNetBalances}, focused on how it
 * folds settlements into the expense-derived balances:
 *   - PENDING settlements are only a plan and must not move any balance
 *   - PAID settlements represent cash that actually changed hands, so they pull
 *     both the payer and the receiver toward zero by the settlement amount
 *
 * Repositories are mocked — no database or Spring context is involved.
 */
class BalanceServiceTest {

    private static final Long GROUP = 42L;
    private static final Long ALICE = 1L; // paid the expense  -> is owed money
    private static final Long BOB = 2L;   // owes his share     -> owes money

    private SettlementRepository settlementRepository;
    private BalanceService balanceService;

    @BeforeEach
    void setUp() {
        ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
        ExpenseSplitRepository expenseSplitRepository = mock(ExpenseSplitRepository.class);
        settlementRepository = mock(SettlementRepository.class);
        GroupAccessGuard groupAccessGuard = mock(GroupAccessGuard.class);
        balanceService = new BalanceService(
                expenseRepository, expenseSplitRepository, settlementRepository, groupAccessGuard);

        // Every scenario shares the same single expense: Alice paid 100, split
        // equally 50/50 with Bob. From expenses alone: Alice +50, Bob -50.
        Expense expense = mock(Expense.class);
        when(expense.getId()).thenReturn(100L);
        when(expense.getPaidBy()).thenReturn(ALICE);
        when(expense.getAmount()).thenReturn(new BigDecimal("100.00"));
        when(expenseRepository.findByGroupIdOrderByExpenseDateDesc(GROUP))
                .thenReturn(List.of(expense));
        when(expenseSplitRepository.findByIdExpenseId(100L)).thenReturn(List.of(
                new ExpenseSplit(100L, ALICE, new BigDecimal("50.00")),
                new ExpenseSplit(100L, BOB, new BigDecimal("50.00"))));
    }

    private static Settlement settlement(Long fromUserId, Long toUserId, String amount, boolean paid) {
        Settlement s = new Settlement(GROUP, fromUserId, toUserId, new BigDecimal(amount));
        if (paid) {
            s.markPaid();
        }
        return s;
    }

    /** Case 1: only expenses, no settlements — balances reflect just the expenses. */
    @Test
    void expensesOnly_noSettlements_balancesReflectJustTheExpenses() {
        when(settlementRepository.findByGroupId(GROUP)).thenReturn(List.of());

        Map<Long, BigDecimal> balances = balanceService.calculateNetBalances(GROUP);

        assertEquals(0, new BigDecimal("50.00").compareTo(balances.get(ALICE)));
        assertEquals(0, new BigDecimal("-50.00").compareTo(balances.get(BOB)));
    }

    /** Case 2: a PENDING settlement must be treated as if it does not exist yet. */
    @Test
    void pendingSettlement_isIgnored_balancesUnchanged() {
        // This pending payment would fully clear the debt IF it counted — it must not.
        when(settlementRepository.findByGroupId(GROUP)).thenReturn(List.of(
                settlement(BOB, ALICE, "50.00", false)));

        Map<Long, BigDecimal> balances = balanceService.calculateNetBalances(GROUP);

        assertEquals(0, new BigDecimal("50.00").compareTo(balances.get(ALICE)));
        assertEquals(0, new BigDecimal("-50.00").compareTo(balances.get(BOB)));
    }

    /** Case 3: a PAID settlement moves both parties toward zero by its amount. */
    @Test
    void paidSettlement_movesPayerAndReceiverTowardZeroByTheAmount() {
        // Bob pays Alice 20 of the 50 he owes.
        when(settlementRepository.findByGroupId(GROUP)).thenReturn(List.of(
                settlement(BOB, ALICE, "20.00", true)));

        Map<Long, BigDecimal> balances = balanceService.calculateNetBalances(GROUP);

        // Bob (payer) was -50, moves up by 20 toward zero -> -30.
        assertEquals(0, new BigDecimal("-30.00").compareTo(balances.get(BOB)));
        // Alice (receiver) was +50, decreases by 20 -> +30.
        assertEquals(0, new BigDecimal("30.00").compareTo(balances.get(ALICE)));
    }

    /** Case 4: a PAID settlement that covers the whole debt leaves both at exactly zero. */
    @Test
    void paidSettlement_coveringTheFullDebt_leavesBothAtExactlyZero() {
        when(settlementRepository.findByGroupId(GROUP)).thenReturn(List.of(
                settlement(BOB, ALICE, "50.00", true)));

        Map<Long, BigDecimal> balances = balanceService.calculateNetBalances(GROUP);

        assertEquals(0, BigDecimal.ZERO.compareTo(balances.get(ALICE)));
        assertEquals(0, BigDecimal.ZERO.compareTo(balances.get(BOB)));
    }
}
