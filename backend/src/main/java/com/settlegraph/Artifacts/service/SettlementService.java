package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.entity.AuditLog;
import com.settlegraph.Artifacts.entity.Settlement;
import com.settlegraph.Artifacts.entity.User;
import com.settlegraph.Artifacts.exception.ForbiddenException;
import com.settlegraph.Artifacts.exception.NotFoundException;
import com.settlegraph.Artifacts.repository.AuditLogRepository;
import com.settlegraph.Artifacts.repository.SettlementRepository;
import com.settlegraph.Artifacts.repository.UserRepository;
import com.settlegraph.Artifacts.security.GroupAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    @Transactional
    public Settlement markPaid(Long groupId, Long settlementId, Long performedByUserId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new NotFoundException("Settlement not found"));

        // The settlement must actually live under the group in the request path.
        if (!settlement.getGroupId().equals(groupId)) {
            throw new NotFoundException("Settlement not found in this group");
        }

        // Only the two people in the settlement, or a member of its group, may settle it.
        boolean isParticipant = performedByUserId.equals(settlement.getFromUserId())
                || performedByUserId.equals(settlement.getToUserId());
        boolean isGroupMember = groupAccessGuard.isMember(settlement.getGroupId(), performedByUserId);
        if (!isParticipant && !isGroupMember) {
            throw new ForbiddenException("You are not allowed to settle this payment");
        }

        settlement.markPaid();
        settlement = settlementRepository.save(settlement);

        auditLogRepository.save(new AuditLog(settlement.getGroupId(), performedByUserId,
                "Marked settlement of " + settlement.getAmount() + " as paid"));

        return settlement;
    }
}
