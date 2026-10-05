package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.entity.AuditLog;
import com.settlegraph.Artifacts.entity.Settlement;
import com.settlegraph.Artifacts.entity.User;
import com.settlegraph.Artifacts.exception.NotFoundException;
import com.settlegraph.Artifacts.repository.AuditLogRepository;
import com.settlegraph.Artifacts.repository.SettlementRepository;
import com.settlegraph.Artifacts.repository.UserRepository;
import com.settlegraph.Artifacts.security.GroupAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class SettlementService {

    private final BalanceService balanceService;
    private final DebtSimplificationService debtSimplificationService;
    private final SettlementRepository settlementRepository;
    private final AuditLogRepository auditLogRepository;
    private final GroupAccessGuard groupAccessGuard;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public SettlementService(BalanceService balanceService,
                              DebtSimplificationService debtSimplificationService,
                              SettlementRepository settlementRepository,
                              AuditLogRepository auditLogRepository,
                              GroupAccessGuard groupAccessGuard,
                              NotificationService notificationService,
                              UserRepository userRepository) {
        this.balanceService = balanceService;
        this.debtSimplificationService = debtSimplificationService;
        this.settlementRepository = settlementRepository;
        this.auditLogRepository = auditLogRepository;
        this.groupAccessGuard = groupAccessGuard;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    /**
     * Computes current balances, runs the debt-simplification algorithm, and
     * saves the resulting minimal payment plan as PENDING settlements.
     */
    @Transactional
    public List<Settlement> generateSettlementPlan(Long groupId, Long requestedByUserId) {
        groupAccessGuard.requireMember(groupId, requestedByUserId);

        // A plan is a fresh proposal each time it's generated. Clear any prior
        // PENDING settlements for this group first, so repeated calls don't stack
        // up duplicate rows for the same debt. PAID settlements are real history
        // (and the balance calc nets them out) — those stay.
        settlementRepository.deleteByGroupIdAndStatus(groupId, Settlement.Status.PENDING);

        Map<Long, BigDecimal> balances = balanceService.calculateNetBalances(groupId);
        List<DebtSimplificationService.Payment> payments = debtSimplificationService.simplify(balances);

        List<Settlement> settlements = payments.stream()
                .map(p -> settlementRepository.save(
                        new Settlement(groupId, p.fromUserId(), p.toUserId(), p.amount())))
                .toList();

        auditLogRepository.save(new AuditLog(groupId, requestedByUserId,
                "Generated a settlement plan with " + settlements.size() + " payment(s)"));

        // Notify BOTH sides of every settlement in the plan — one notification
        // per obligation. If a person is the debtor in one settlement and the
        // creditor in another, they get two separate notifications, one for
        // each obligation; we deliberately do NOT collapse them.
        for (Settlement s : settlements) {
            String debtorName = userName(s.getFromUserId());
            String creditorName = userName(s.getToUserId());
            notificationService.create(s.getFromUserId(),
                    "You owe ₹" + s.getAmount() + " to " + creditorName);
            notificationService.create(s.getToUserId(),
                    debtorName + " owes you ₹" + s.getAmount());
        }

        return settlements;
    }

    /** A display name for notification text; falls back gracefully if the user row is gone. */
    private String userName(Long userId) {
        return userRepository.findById(userId).map(User::getName).orElse("Someone");
    }

    public List<Settlement> getSettlementsForGroup(Long groupId, Long requestingUserId) {
        groupAccessGuard.requireMember(groupId, requestingUserId);
        return settlementRepository.findByGroupId(groupId);
    }

    /**
     * Authorization first, against the group in the request path, before the
     * settlement is even loaded. (Settlement participants are always group
     * members — there is no leave/remove-member flow — so the membership
     * check alone covers them.)
     *
     * The PENDING -> PAID transition is a conditional UPDATE rather than
     * read-check-save, so two concurrent requests can't both succeed and
     * write two audit rows; whichever loses gets a 400.
     */
    @Transactional
    public Settlement markPaid(Long groupId, Long settlementId, Long performedByUserId) {
        groupAccessGuard.requireMember(groupId, performedByUserId);

        // Scoped to the group in the path: a settlement belonging to another
        // group is never read, and gets the same 404 as one that doesn't
        // exist — so its existence can't be probed from outside that group.
        Settlement settlement = settlementRepository.findByIdAndGroupId(settlementId, groupId)
                .orElseThrow(() -> new NotFoundException("Settlement not found"));

        if (settlementRepository.markPaidIfPending(settlementId, LocalDateTime.now()) == 0) {
            throw new IllegalArgumentException("This settlement has already been marked as paid");
        }

        auditLogRepository.save(new AuditLog(groupId, performedByUserId,
                "Marked settlement of " + settlement.getAmount() + " as paid"));

        // The UPDATE cleared the persistence context; re-read the committed state.
        return settlementRepository.findById(settlementId)
                .orElseThrow(() -> new NotFoundException("Settlement not found"));
    }
}
