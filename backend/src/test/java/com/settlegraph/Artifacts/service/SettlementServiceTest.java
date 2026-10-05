package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.entity.Settlement;
import com.settlegraph.Artifacts.entity.User;
import com.settlegraph.Artifacts.exception.ForbiddenException;
import com.settlegraph.Artifacts.exception.NotFoundException;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link SettlementService#generateSettlementPlan}:
 *   - "a plan is a fresh proposal each time": generating clears prior PENDING
 *     settlements first, so repeated calls against unchanged balances don't
 *     stack up duplicate rows
 *   - the notification fan-out: BOTH sides of every settlement in the plan get
 *     a notification — one per obligation, worded for that person's role
 * and {@link SettlementService#markPaid}: authorization before any read, a
 * settlement in another group looks exactly like a missing one, and an
 * already-PAID settlement is rejected without a second audit row. (The
 * concurrent case depends on the real conditional UPDATE — verified against
 * Postgres, not here.)
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
    private GroupAccessGuard groupAccessGuard;
    private AuditLogRepository auditLogRepository;
    private SettlementService settlementService;

    @BeforeEach
    void setUp() {
        store = new ArrayList<>();

        BalanceService balanceService = mock(BalanceService.class);
        settlementRepository = mock(SettlementRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        groupAccessGuard = mock(GroupAccessGuard.class);
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

    // ---- markPaid ----

    private static final Long SETTLEMENT_ID = 500L;
    private static final Long OUTSIDER = 99L;

    /** Stubs the group-scoped lookup; any other (id, group) pair finds nothing, like the real query. */
    private Settlement pendingSettlementIn(Long groupId) {
        Settlement s = new Settlement(groupId, BOB, ALICE, new BigDecimal("50.00"));
        when(settlementRepository.findByIdAndGroupId(SETTLEMENT_ID, groupId)).thenReturn(Optional.of(s));
        return s;
    }

    @Test
    void markPaid_byNonMember_isRejectedBeforeTheSettlementIsLoaded() {
        pendingSettlementIn(GROUP);
        doThrow(new ForbiddenException("You are not a member of this group"))
                .when(groupAccessGuard).requireMember(GROUP, OUTSIDER);

        assertThrows(ForbiddenException.class,
                () -> settlementService.markPaid(GROUP, SETTLEMENT_ID, OUTSIDER));

        // Rejected before the settlement row was even read, let alone changed.
        verify(settlementRepository, never()).findByIdAndGroupId(any(), any());
        verify(settlementRepository, never()).findById(any());
        verify(settlementRepository, never()).markPaidIfPending(any(), any());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void markPaid_onAPendingSettlement_transitionsIt_auditsOnce_andReturnsTheReReadRow() {
        pendingSettlementIn(GROUP);
        when(settlementRepository.markPaidIfPending(eq(SETTLEMENT_ID), any())).thenReturn(1);
        // The UPDATE clears the persistence context, so the service must hand back
        // a fresh read of the committed row — not the stale PENDING instance.
        Settlement committed = new Settlement(GROUP, BOB, ALICE, new BigDecimal("50.00"));
        committed.markPaid();
        when(settlementRepository.findById(SETTLEMENT_ID)).thenReturn(Optional.of(committed));

        Settlement result = settlementService.markPaid(GROUP, SETTLEMENT_ID, BOB);

        assertSame(committed, result);
        assertEquals(Settlement.Status.PAID, result.getStatus());
        verify(groupAccessGuard).requireMember(GROUP, BOB);
        verify(settlementRepository).markPaidIfPending(eq(SETTLEMENT_ID), any());
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    void markPaid_onAnAlreadyPaidSettlement_isRejected_andWritesNoAuditRow() {
        // Audit bug 4: used to return 200 again and write a second audit row.
        // The conditional UPDATE changing 0 rows is what "already PAID" looks like.
        pendingSettlementIn(GROUP);
        when(settlementRepository.markPaidIfPending(eq(SETTLEMENT_ID), any())).thenReturn(0);

        assertThrows(IllegalArgumentException.class,
                () -> settlementService.markPaid(GROUP, SETTLEMENT_ID, BOB));

        verifyNoInteractions(auditLogRepository);
        verify(settlementRepository, never()).save(any());
    }

    @Test
    void markPaid_settlementFromAnotherGroup_looksExactlyLikeAMissingOne_andNothingIsChanged() {
        pendingSettlementIn(777L); // lives in a different group than the path says

        NotFoundException otherGroup = assertThrows(NotFoundException.class,
                () -> settlementService.markPaid(GROUP, SETTLEMENT_ID, BOB));
        NotFoundException missing = assertThrows(NotFoundException.class,
                () -> settlementService.markPaid(GROUP, 12345L, BOB));

        // Same message, so a member of GROUP can't probe whether an ID exists elsewhere.
        assertEquals(missing.getMessage(), otherGroup.getMessage());
        // Only the group-scoped lookup is used — never an unscoped read of another group's row.
        verify(settlementRepository, never()).findById(any());
        verify(settlementRepository, never()).markPaidIfPending(any(), any());
        verifyNoInteractions(auditLogRepository);
    }
}
