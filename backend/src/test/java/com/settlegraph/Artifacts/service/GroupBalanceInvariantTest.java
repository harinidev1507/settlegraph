package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.dto.CreateExpenseRequest;
import com.settlegraph.Artifacts.entity.Expense;
import com.settlegraph.Artifacts.entity.ExpenseSplit;
import com.settlegraph.Artifacts.entity.GroupMember;
import com.settlegraph.Artifacts.entity.Settlement;
import com.settlegraph.Artifacts.repository.AuditLogRepository;
import com.settlegraph.Artifacts.repository.ExpenseRepository;
import com.settlegraph.Artifacts.repository.ExpenseSplitRepository;
import com.settlegraph.Artifacts.repository.GroupMemberRepository;
import com.settlegraph.Artifacts.repository.SettlementRepository;
import com.settlegraph.Artifacts.security.GroupAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * The CLAUDE.md balance invariant: within a group, net balances always sum to
 * EXACTLY zero — for every split type, after every single expense — and
 * paying the generated settlement plan leaves every member at exactly zero.
 *
 * Unlike {@link BalanceServiceTest}, which feeds hand-built splits to
 * BalanceService, this wires the REAL {@link ExpenseService} to the REAL
 * {@link BalanceService} through shared in-memory repositories, so it checks
 * the splits ExpenseService actually produces. The repositories are Mockito
 * mocks backed by lists — no database or Spring context. Fixed seed, so a
 * failure is reproducible.
 */
class GroupBalanceInvariantTest {

    private static final Long GROUP = 42L;
    private static final List<Long> MEMBERS = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L);

    private final List<Expense> expenses = new ArrayList<>();
    private final List<ExpenseSplit> splits = new ArrayList<>();
    private final List<Settlement> settlements = new ArrayList<>();
    private final Random random = new Random(20261006L);
    private ExpenseService expenseService;
    private BalanceService balanceService;

    @BeforeEach
    void setUp() {
        ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
        ExpenseSplitRepository expenseSplitRepository = mock(ExpenseSplitRepository.class);
        GroupMemberRepository groupMemberRepository = mock(GroupMemberRepository.class);
        SettlementRepository settlementRepository = mock(SettlementRepository.class);
        GroupAccessGuard groupAccessGuard = mock(GroupAccessGuard.class); // everyone passes

        // save() hands each expense the next id, as the IDENTITY column would, so
        // its splits can be found again by expense id.
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense saved = spy(inv.<Expense>getArgument(0));
            doReturn((long) expenses.size() + 1).when(saved).getId();
            expenses.add(saved);
            return saved;
        });
        when(expenseSplitRepository.save(any(ExpenseSplit.class))).thenAnswer(inv -> {
            splits.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(expenseRepository.findByGroupIdOrderByExpenseDateDesc(GROUP))
                .thenAnswer(inv -> List.copyOf(expenses));
        when(expenseSplitRepository.findByIdExpenseId(anyLong())).thenAnswer(inv -> {
            Long expenseId = inv.getArgument(0);
            return splits.stream().filter(s -> s.getId().getExpenseId().equals(expenseId)).toList();
        });
        when(settlementRepository.findByGroupId(GROUP)).thenAnswer(inv -> List.copyOf(settlements));
        when(groupMemberRepository.findByIdGroupId(GROUP))
                .thenReturn(MEMBERS.stream().map(id -> new GroupMember(id, GROUP)).toList());

        expenseService = new ExpenseService(expenseRepository, expenseSplitRepository, groupMemberRepository,
                mock(AuditLogRepository.class), groupAccessGuard, mock(NotificationService.class));
        balanceService = new BalanceService(expenseRepository, expenseSplitRepository,
                settlementRepository, groupAccessGuard);
    }

    @Test
    void equalSplits_balancesSumToExactlyZero_afterEveryExpense() {
        addManyExpensesCheckingTheInvariantEachTime("EQUAL");
    }

    @Test
    void percentageSplits_balancesSumToExactlyZero_afterEveryExpense() {
        addManyExpensesCheckingTheInvariantEachTime("PERCENTAGE");
    }

    @Test
    void sharesSplits_balancesSumToExactlyZero_afterEveryExpense() {
        addManyExpensesCheckingTheInvariantEachTime("SHARES");
    }

    @Test
    void exactSplits_balancesSumToExactlyZero_afterEveryExpense() {
        addManyExpensesCheckingTheInvariantEachTime("EXACT");
    }

    @Test
    void mixedSplitTypes_settledWithTheGeneratedPlan_leaveEveryMemberAtExactlyZero() {
        // The user-visible form of the audit bug: one person left owed 0.01
        // forever after everyone paid the plan. Stronger than "sums to zero":
        // after paying the plan, NO individual balance may be left over.
        for (String splitType : List.of("EQUAL", "PERCENTAGE", "SHARES", "EXACT")) {
            for (int i = 0; i < 10; i++) {
                addRandomExpense(splitType);
            }
        }
        Map<Long, BigDecimal> before = balanceService.calculateNetBalances(GROUP);
        assertTrue(before.values().stream().anyMatch(b -> b.signum() != 0),
                "vacuous: nobody owed anything before settling");

        for (DebtSimplificationService.Payment p : new DebtSimplificationService().simplify(before)) {
            Settlement paid = new Settlement(GROUP, p.fromUserId(), p.toUserId(), p.amount());
            paid.markPaid();
            settlements.add(paid);
        }

        Map<Long, BigDecimal> after = balanceService.calculateNetBalances(GROUP);
        for (Map.Entry<Long, BigDecimal> entry : after.entrySet()) {
            assertEquals(0, entry.getValue().signum(),
                    "user " + entry.getKey() + " left at " + entry.getValue() + " after paying the plan");
        }
    }

    private void addManyExpensesCheckingTheInvariantEachTime(String splitType) {
        for (int i = 1; i <= 50; i++) {
            String added = addRandomExpense(splitType);
            BigDecimal sum = sum(balanceService.calculateNetBalances(GROUP).values());
            assertEquals(0, sum.signum(), "after expense " + i + " (" + added + ") balances summed to " + sum);
        }
        assertTrue(balanceService.calculateNetBalances(GROUP).values().stream().anyMatch(b -> b.signum() != 0),
                "vacuous: every balance was zero, so the sum check proved nothing");
    }

    /**
     * Adds one valid expense of the given type: random payer, random amount
     * from 0.01 to 10,000.00, random subset of 1..7 participants (the payer
     * need not be one of them). Returns a description for failure messages.
     */
    private String addRandomExpense(String splitType) {
        Long payer = MEMBERS.get(random.nextInt(MEMBERS.size()));
        int amountCents = 1 + random.nextInt(1_000_000);
        List<Long> shuffled = new ArrayList<>(MEMBERS);
        Collections.shuffle(shuffled, random);
        int participantCount = 1 + random.nextInt(MEMBERS.size());
        if (splitType.equals("EXACT")) {
            participantCount = Math.min(participantCount, amountCents); // each needs at least 1 cent
        }
        List<Long> participants = shuffled.subList(0, participantCount);

        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setGroupId(GROUP);
        request.setAmount(BigDecimal.valueOf(amountCents, 2));
        request.setDescription("invariant");
        request.setSplitType(splitType);

        Map<Long, BigDecimal> values = new HashMap<>();
        switch (splitType) {
            case "EQUAL" -> request.setParticipantIds(List.copyOf(participants));
            case "PERCENTAGE" -> {
                int[] basisPoints = positivePartition(10_000, participantCount);
                for (int i = 0; i < participantCount; i++) {
                    values.put(participants.get(i), BigDecimal.valueOf(basisPoints[i], 2));
                }
            }
            case "SHARES" -> participants.forEach(id -> values.put(id, BigDecimal.valueOf(1 + random.nextInt(10))));
            case "EXACT" -> {
                int[] cents = positivePartition(amountCents, participantCount);
                for (int i = 0; i < participantCount; i++) {
                    values.put(participants.get(i), BigDecimal.valueOf(cents[i], 2));
                }
            }
            default -> throw new IllegalArgumentException(splitType);
        }
        if (!splitType.equals("EQUAL")) {
            request.setSplits(values);
        }

        expenseService.addExpense(request, payer);
        return splitType + " " + request.getAmount() + " paid by " + payer + " among " + participants
                + (values.isEmpty() ? "" : " values=" + values);
    }

    /** Splits {@code total} into {@code parts} random positive integers that sum to it. */
    private int[] positivePartition(int total, int parts) {
        int[] result = new int[parts];
        int remaining = total;
        for (int i = 0; i < parts - 1; i++) {
            // Leave at least 1 for each part still to come.
            result[i] = 1 + random.nextInt(remaining - (parts - i - 1));
            remaining -= result[i];
        }
        result[parts - 1] = remaining;
        return result;
    }

    private static BigDecimal sum(Collection<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
