package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.entity.Settlement;
import com.settlegraph.Artifacts.entity.User;
import com.settlegraph.Artifacts.repository.AuditLogRepository;
import com.settlegraph.Artifacts.repository.SettlementRepository;
import com.settlegraph.Artifacts.repository.UserRepository;
import com.settlegraph.Artifacts.security.GroupAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SettlementService#generateSettlementPlan}:
 *   - "a plan is a fresh proposal each time": generating clears prior PENDING
 *     settlements first, so repeated calls against unchanged balances don't
 *     stack up duplicate rows
 *   - the notification fan-out: BOTH sides of every settlement in the plan get
 *     a notification — one per obligation, worded for that person's role
 *
 * The settlement repository is backed by a tiny in-memory list so that save /
 * deleteByGroupIdAndStatus actually interact. Other collaborators are mocked —
 * no database or Spring context.
 */
class SettlementServiceTest {

    private static final Long GROUP = 42L;
    private static final Long REQUESTED_BY = 7L;
    private static final Long ALICE = 1L; // owed 50 -> creditor in the plan
    private static final Long BOB = 2L;   // owes 50 -> debtor in the plan

    private List<Settlement> store;
    private SettlementRepository settlementRepository;
    private NotificationService notificationService;
    private SettlementService settlementService;

    @BeforeEach
    void setUp() {
        store = new ArrayList<>();

        BalanceService balanceService = mock(BalanceService.class);
        settlementRepository = mock(SettlementRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        GroupAccessGuard groupAccessGuard = mock(GroupAccessGuard.class);
        notificationService = mock(NotificationService.class);
        UserRepository userRepository = mock(UserRepository.class);
        // The debt-simplification algorithm has no external deps — use the real one.
        DebtSimplificationService debtSimplificationService = new DebtSimplificationService();

        settlementService = new SettlementService(balanceService, debtSimplificationService,
                settlementRepository, auditLogRepository, groupAccessGuard,
                notificationService, userRepository);

        // Balances never change between generate calls: Bob owes Alice 50.
        when(balanceService.calculateNetBalances(GROUP)).thenReturn(Map.of(
                ALICE, new BigDecimal("50.00"),
                BOB, new BigDecimal("-50.00")));

        // Names used in the notification text.
        when(userRepository.findById(ALICE)).thenReturn(Optional.of(
                new User("alice", "Alice", "alice@example.com", "x")));
        when(userRepository.findById(BOB)).thenReturn(Optional.of(
                new User("bob", "Bob", "bob@example.com", "x")));

        // save() appends to the store and hands the row back.
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(inv -> {
            Settlement s = inv.getArgument(0);
            store.add(s);
            return s;
        });

        // deleteByGroupIdAndStatus() removes matching rows from the store.
        when(settlementRepository.deleteByGroupIdAndStatus(eq(GROUP), any(Settlement.Status.class)))
                .thenAnswer(inv -> {
                    Settlement.Status status = inv.getArgument(1);
                    long removed = store.stream()
                            .filter(s -> s.getGroupId().equals(GROUP) && s.getStatus() == status)
                            .count();
                    store.removeIf(s -> s.getGroupId().equals(GROUP) && s.getStatus() == status);
                    return removed;
                });
    }

    @Test
    void generatingTwiceWithUnchangedBalances_leavesExactlyOnePendingSettlement() {
        settlementService.generateSettlementPlan(GROUP, REQUESTED_BY);
        settlementService.generateSettlementPlan(GROUP, REQUESTED_BY);

        long pendingCount = store.stream()
                .filter(s -> s.getStatus() == Settlement.Status.PENDING)
                .count();

        assertEquals(1, pendingCount,
                "second generate should have replaced the first PENDING plan, not added to it");
    }

    @Test
    void generatingAPlan_notifiesBothSidesOfEachSettlement_wordedForTheirRole() {
        List<Settlement> plan = settlementService.generateSettlementPlan(GROUP, REQUESTED_BY);

        // Sanity: the plan is the single expected obligation, Bob -> Alice 50.
        assertEquals(1, plan.size());
        assertEquals(BOB, plan.get(0).getFromUserId());
        assertEquals(ALICE, plan.get(0).getToUserId());

        // The debtor (Bob) is told he owes Alice.
        verify(notificationService).create(eq(BOB), argThat(m ->
                m.contains("You owe") && m.contains("50.00") && m.contains("Alice")));

        // The creditor (Alice) is told Bob owes her.
        verify(notificationService).create(eq(ALICE), argThat(m ->
                m.contains("owes you") && m.contains("50.00") && m.contains("Bob")));

        // Exactly two notifications for a one-settlement plan — one per side, not one.
        verify(notificationService, times(2)).create(any(), anyString());
    }
}
