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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

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

        BigDecimal total = shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.subtract(request.getAmount()).abs().compareTo(new BigDecimal("0.02")) > 0) {
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
            case "EXACT" -> request.getSplits();

            case "PERCENTAGE" -> {
                Map<Long, BigDecimal> result = new java.util.HashMap<>();
                for (var entry : request.getSplits().entrySet()) {
                    BigDecimal share = request.getAmount()
                            .multiply(entry.getValue())
                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                    result.put(entry.getKey(), share);
                }
                yield result;
            }

            case "SHARES" -> {
                BigDecimal totalShares = request.getSplits().values()
                        .stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                Map<Long, BigDecimal> result = new java.util.HashMap<>();
                for (var entry : request.getSplits().entrySet()) {
                    BigDecimal share = request.getAmount()
                            .multiply(entry.getValue())
                            .divide(totalShares, 2, RoundingMode.HALF_UP);
                    result.put(entry.getKey(), share);
                }
                yield result;
            }

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
                BigDecimal share = request.getAmount()
                        .divide(new BigDecimal(participantIds.size()), 2, RoundingMode.HALF_UP);
                Map<Long, BigDecimal> result = new java.util.HashMap<>();
                for (Long userId : participantIds) {
                    result.put(userId, share);
                }
                yield result;
            }

            default -> throw new IllegalArgumentException(
                    "Unknown splitType: " + splitType + " (expected EQUAL, EXACT, PERCENTAGE or SHARES)");
        };
    }

    public List<Expense> getExpensesForGroup(Long groupId, Long requestingUserId) {
        groupAccessGuard.requireMember(groupId, requestingUserId);
        return expenseRepository.findByGroupIdOrderByExpenseDateDesc(groupId);
    }
}
