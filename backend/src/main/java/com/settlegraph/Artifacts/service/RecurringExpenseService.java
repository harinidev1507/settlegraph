package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.entity.AuditLog;
import com.settlegraph.Artifacts.entity.Expense;
import com.settlegraph.Artifacts.entity.ExpenseSplit;
import com.settlegraph.Artifacts.entity.RecurrenceFrequency;
import com.settlegraph.Artifacts.repository.AuditLogRepository;
import com.settlegraph.Artifacts.repository.ExpenseRepository;
import com.settlegraph.Artifacts.repository.ExpenseSplitRepository;
import com.settlegraph.Artifacts.repository.GroupMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Generates the next occurrence of each recurring expense once its period is
 * due. Idempotency is anchored on a calendar-derived period key
 * ("2026-09" for a monthly expense) rather than on job-run state: a
 * generated occurrence is uniquely identified by (source expense, period),
 * enforced by a DB unique index (V4 migration) in addition to the
 * exists-check below. Running the whole method twice in a row is therefore
 * safe — the second run's exists-check finds the row the first run already
 * committed and skips it, and even a true concurrent collision would be
 * rejected by the unique index rather than produce a duplicate.
 *
 * <p>Each template is generated in its own REQUIRES_NEW transaction. A
 * constraint violation on one template (the unique index firing on a real
 * concurrent collision) rolls back only that template's occurrence; every
 * other template in the same run still commits. Without this, one failure
 * poisoned the single shared transaction and silently dropped the whole
 * run's output.
 */
@Service
public class RecurringExpenseService {

    private static final Logger log = LoggerFactory.getLogger(RecurringExpenseService.class);

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final AuditLogRepository auditLogRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final NotificationService notificationService;
    private final TransactionTemplate perTemplateTransaction;

    public RecurringExpenseService(ExpenseRepository expenseRepository,
                                    ExpenseSplitRepository expenseSplitRepository,
                                    AuditLogRepository auditLogRepository,
                                    GroupMemberRepository groupMemberRepository,
                                    NotificationService notificationService,
                                    PlatformTransactionManager transactionManager) {
        this.expenseRepository = expenseRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.auditLogRepository = auditLogRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.notificationService = notificationService;
        this.perTemplateTransaction = new TransactionTemplate(transactionManager);
        this.perTemplateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelay = 24 * 60 * 60 * 1000)
    public void runScheduledCheck() {
        generateDueRecurrences();
    }

    /**
     * Callable directly (e.g. from tests) to run the same check the
     * schedule triggers, without waiting for a real day to pass.
     *
     * Deliberately NOT {@code @Transactional}: each template runs inside
     * its own REQUIRES_NEW transaction below, so a failure in one is
     * contained to that one and the loop moves on to the next template.
     */
    public void generateDueRecurrences() {
        LocalDate today = LocalDate.now();
        for (Expense template : expenseRepository.findByRecurringTrue()) {
            try {
                perTemplateTransaction.executeWithoutResult(status -> generateIfDue(template, today));
            } catch (DataIntegrityViolationException e) {
                // Most likely the unique (recurring_source_id, recurrence_period)
                // index rejecting a concurrent duplicate. That occurrence already
                // exists (the other writer won), so there is nothing to retry —
                // log it and carry on with the remaining templates.
                log.warn("Skipping recurring expense template {} for this run: {}",
                        template.getId(), e.getMostSpecificCause().getMessage());
            }
        }
    }

    private void generateIfDue(Expense template, LocalDate today) {
        if (template.getRecurrenceFrequency() != RecurrenceFrequency.MONTHLY) {
            return;
        }

        String anchorPeriod = YearMonth.from(template.getExpenseDate()).toString();
        String currentPeriod = YearMonth.from(today).toString();
        if (currentPeriod.equals(anchorPeriod) || !isDue(template, today)) {
            return;
        }
        if (expenseRepository.existsByRecurringSourceIdAndRecurrencePeriod(template.getId(), currentPeriod)) {
            return;
        }

        Expense occurrence = new Expense(template.getGroupId(), template.getPaidBy(),
                template.getAmount(), template.getCurrency(), template.getCategory(), template.getDescription());
        occurrence.setRecurringSourceId(template.getId());
        occurrence.setRecurrencePeriod(currentPeriod);
        occurrence = expenseRepository.save(occurrence);

        for (ExpenseSplit split : expenseSplitRepository.findByIdExpenseId(template.getId())) {
            expenseSplitRepository.save(new ExpenseSplit(
                    occurrence.getId(), split.getId().getUserId(), split.getShareAmount()));
        }

        auditLogRepository.save(new AuditLog(template.getGroupId(), template.getPaidBy(),
                "Generated recurring expense \"" + template.getDescription() + "\" for " + template.getAmount()));

        List<Long> memberIds = groupMemberRepository.findByIdGroupId(template.getGroupId())
                .stream().map(m -> m.getId().getUserId()).toList();
        for (Long memberId : memberIds) {
            if (!memberId.equals(template.getPaidBy())) {
                notificationService.create(memberId,
                        "Recurring expense \"" + template.getDescription() + "\" for "
                                + template.getAmount() + " was added");
            }
        }
    }

    private boolean isDue(Expense template, LocalDate today) {
        int anchorDay = template.getExpenseDate().getDayOfMonth();
        int effectiveAnchorDay = Math.min(anchorDay, today.lengthOfMonth());
        return today.getDayOfMonth() >= effectiveAnchorDay;
    }
}
