package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.dto.CreateExpenseRequest;
import com.settlegraph.Artifacts.entity.AuditLog;
import com.settlegraph.Artifacts.entity.Expense;
import com.settlegraph.Artifacts.entity.ExpenseSplit;
import com.settlegraph.Artifacts.entity.RecurrenceFrequency;
import com.settlegraph.Artifacts.exception.NotFoundException;
import com.settlegraph.Artifacts.repository.AuditLogRepository;
import com.settlegraph.Artifacts.repository.ExpenseRepository;
import com.settlegraph.Artifacts.repository.ExpenseSplitRepository;
import com.settlegraph.Artifacts.repository.GroupMemberRepository;
import com.settlegraph.Artifacts.security.GroupAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final AuditLogRepository auditLogRepository;
    private final GroupAccessGuard groupAccessGuard;
    private final NotificationService notificationService;

    public ExpenseService(ExpenseRepository expenseRepository,
                           ExpenseSplitRepository expenseSplitRepository,
                           GroupMemberRepository groupMemberRepository,
                           AuditLogRepository auditLogRepository,
                           GroupAccessGuard groupAccessGuard,
                           NotificationService notificationService) {
        this.expenseRepository = expenseRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.auditLogRepository = auditLogRepository;
        this.groupAccessGuard = groupAccessGuard;
        this.notificationService = notificationService;
    }

    /**
     * Adds an expense and its splits in a single transaction — either both
     * succeed, or neither does. This matters: an expense that exists without
     * any splits attached would silently break every balance calculation
     * that reads it later.
     *
     * Order matters too: membership check, then every validation, and only
     * then the first write. A rejected request never touches the database.
     */
    @Transactional
    public Expense addExpense(CreateExpenseRequest request, Long paidByUserId) {
        groupAccessGuard.requireMember(request.getGroupId(), paidByUserId);

        Map<Long, BigDecimal> shares = calculateShares(request);

        // Everyone assigned a share must actually be a member of the group —
        // whether they came from an explicit participant list (EQUAL) or the
        // splits map (EXACT / PERCENTAGE / SHARES).
        Set<Long> memberIds = groupMemberRepository.findByIdGroupId(request.getGroupId()).stream()
                .map(m -> m.getId().getUserId())
                .collect(Collectors.toSet());
        for (Long userId : shares.keySet()) {
            if (!memberIds.contains(userId)) {
                throw new IllegalArgumentException("User " + userId + " is not a member of this group");
            }
        }

        // Exact, no tolerance: allocated split types always sum exactly, and an
        // EXACT split the user typed that is off by a cent is a mistake to
        // report — accepting it would leave balances that don't sum to zero.
        BigDecimal total = shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(request.getAmount()) != 0) {
            throw new IllegalArgumentException(
                    "Split amounts (" + total + ") do not add up to the expense total (" + request.getAmount() + ")");
        }

        // A recurring expense is a normal expense (this month's) that also acts
        // as the template the scheduler copies each following month.
        if (request.isRecurring() && request.getRecurrenceFrequency() == null) {
            throw new IllegalArgumentException("A recurring expense needs a recurrenceFrequency (MONTHLY)");
        }
        if (!request.isRecurring() && request.getRecurrenceFrequency() != null) {
            throw new IllegalArgumentException("recurrenceFrequency only applies when recurring is true");
        }

        Expense expense = new Expense(
                request.getGroupId(), paidByUserId, request.getAmount(),
                request.getCurrency(), request.getCategory(), request.getDescription());
        expense.setRecurring(request.isRecurring());
        expense.setRecurrenceFrequency(request.getRecurrenceFrequency());
        expense = expenseRepository.save(expense);

        for (Map.Entry<Long, BigDecimal> entry : shares.entrySet()) {
            expenseSplitRepository.save(new ExpenseSplit(expense.getId(), entry.getKey(), entry.getValue()));
        }

        String kind = request.isRecurring() ? "recurring expense" : "expense";
        auditLogRepository.save(new AuditLog(request.getGroupId(), paidByUserId,
                "Added " + kind + " \"" + request.getDescription() + "\" for " + request.getAmount()));

        // Notify every OTHER member of the group. The person who added the
        // expense already knows they did it — they don't get a notification
        // about their own action, even though they're a member of the group.
        for (Long memberId : memberIds) {
            if (!memberId.equals(paidByUserId)) {
                notificationService.create(memberId,
                        "New " + kind + " \"" + request.getDescription() + "\" for "
                                + request.getAmount() + " was added");
            }
        }

        return expense;
    }

    /**
     * Turns a recurring template off: it stays as the ordinary expense it
     * already is, but the scheduler stops generating new months from it.
     * Occurrences already generated are real expenses and are kept.
     */
    @Transactional
    public Expense stopRecurring(Long expenseId, Long requestingUserId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new NotFoundException("Expense not found"));
        groupAccessGuard.requireMember(expense.getGroupId(), requestingUserId);

        if (!expense.isRecurring()) {
            throw new IllegalArgumentException("This expense is not recurring");
        }

        expense.setRecurring(false);
        expense = expenseRepository.save(expense);

        auditLogRepository.save(new AuditLog(expense.getGroupId(), requestingUserId,
                "Stopped recurring expense \"" + expense.getDescription() + "\""));

        return expense;
    }

    private Map<Long, BigDecimal> calculateShares(CreateExpenseRequest request) {
        String splitType = request.getSplitType() == null ? "EQUAL" : request.getSplitType().toUpperCase();

        if (!splitType.equals("EQUAL")) {
            Map<Long, BigDecimal> splits = request.getSplits();
            if (splits == null || splits.isEmpty()) {
                throw new IllegalArgumentException(splitType + " split requires a non-empty splits map");
            }
            for (var entry : splits.entrySet()) {
                if (entry.getValue() == null || entry.getValue().signum() <= 0) {
                    throw new IllegalArgumentException(
                            "Split value for user " + entry.getKey() + " must be greater than zero");
                }
            }
        }

        return switch (splitType) {
            case "EXACT" -> {
                // EXACT values are stored as-is in numeric(12,2), which silently
                // rounds a sub-cent value: 33.335 + 33.335 + 33.33 passes the
                // exact-total check as typed (100.000) but is stored as 100.01.
                // (PERCENTAGE / SHARES values are weights, so 33.333% is fine there.)
                for (var entry : request.getSplits().entrySet()) {
                    if (entry.getValue().stripTrailingZeros().scale() > 2) {
                        throw new IllegalArgumentException("Exact split for user " + entry.getKey()
                                + " has more than 2 decimal places (" + entry.getValue().toPlainString() + ")");
                    }
                }
                yield request.getSplits();
            }

            case "PERCENTAGE" -> {
                // Must be checked here, not left to the sum check in addExpense:
                // allocate() normalises by the total weight, so 50% / 40% would
                // otherwise be silently stretched to cover the whole amount.
                BigDecimal totalPercent = request.getSplits().values()
                        .stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                if (totalPercent.compareTo(ONE_HUNDRED) != 0) {
                    throw new IllegalArgumentException(
                            "Percentages must add up to exactly 100 (got " + totalPercent + ")");
                }
                yield allocate(request.getAmount(), request.getSplits());
            }

            case "SHARES" -> allocate(request.getAmount(), request.getSplits());

            case "EQUAL" -> {
                // Split across an explicit participant list supplied by the caller.
                // We deliberately do NOT fall back to "all current group members":
                // that made an expense's split depend on when it was entered
                // relative to people joining, and silently dropped participants
                // who hadn't joined yet.
                List<Long> participantIds = request.getParticipantIds();
                if (participantIds == null || participantIds.isEmpty()) {
                    throw new IllegalArgumentException(
                            "EQUAL split requires an explicit list of participant user IDs");
                }
                if (new HashSet<>(participantIds).size() != participantIds.size()) {
                    throw new IllegalArgumentException("EQUAL split participant list contains duplicates");
                }
                Map<Long, BigDecimal> weights = new HashMap<>();
                for (Long userId : participantIds) {
                    weights.put(userId, BigDecimal.ONE);
                }
                yield allocate(request.getAmount(), weights);
            }

            default -> throw new IllegalArgumentException(
                    "Unknown splitType: " + splitType + " (expected EQUAL, EXACT, PERCENTAGE or SHARES)");
        };
    }

    /**
     * Splits {@code amount} in proportion to {@code weights} so the shares sum
     * to EXACTLY {@code amount} — no cent is created or lost to rounding.
     *
     * Largest-remainder rule: each share is first rounded DOWN to 2dp. The
     * cents that leaves over go one at a time to the participants whose exact
     * share lost the most to that floor (largest fractional remainder), ties
     * broken by ascending user ID so the result is deterministic. Because
     * every share loses strictly less than one cent, the leftover is always
     * between 0 and n-1 cents; anything else is a bug to surface, not paper over.
     */
    private static Map<Long, BigDecimal> allocate(BigDecimal amount, Map<Long, BigDecimal> weights) {
        BigDecimal totalWeight = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, BigDecimal> shares = new HashMap<>();
        // The exact share is amount*w/totalWeight, which may not terminate (1/3).
        // Its remainder over the floor is (amount*w - floored*totalWeight) / totalWeight;
        // the denominator is the same for everyone, so the numerator alone orders them.
        Map<Long, BigDecimal> remainders = new HashMap<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> entry : weights.entrySet()) {
            BigDecimal numerator = amount.multiply(entry.getValue());
            BigDecimal floored = numerator.divide(totalWeight, 2, RoundingMode.DOWN);
            shares.put(entry.getKey(), floored);
            remainders.put(entry.getKey(), numerator.subtract(floored.multiply(totalWeight)));
            allocated = allocated.add(floored);
        }

        int leftoverCents = amount.subtract(allocated).movePointRight(2).intValueExact();
        if (leftoverCents < 0 || leftoverCents >= shares.size()) {
            throw new IllegalStateException("Split allocation left " + leftoverCents
                    + " cents for " + shares.size() + " participants — expected 0 to n-1");
        }

        List<Long> byLargestRemainder = shares.keySet().stream()
                .sorted(Comparator.comparing((Long id) -> remainders.get(id)).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        for (int i = 0; i < leftoverCents; i++) {
            shares.merge(byLargestRemainder.get(i), ONE_CENT, BigDecimal::add);
        }
        return shares;
    }

    public List<Expense> getExpensesForGroup(Long groupId, Long requestingUserId) {
        groupAccessGuard.requireMember(groupId, requestingUserId);
        return expenseRepository.findByGroupIdOrderByExpenseDateDesc(groupId);
    }
}
