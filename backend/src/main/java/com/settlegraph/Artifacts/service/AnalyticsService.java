package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.entity.Expense;
import com.settlegraph.Artifacts.repository.ExpenseRepository;
import com.settlegraph.Artifacts.security.GroupAccessGuard;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-only spend analytics for a group.
 *
 * IMPORTANT: both aggregations sum the EXPENSE amount itself, once per expense
 * row — they never touch {@code expense_split}. Summing split shares would
 * count a single expense once per participant (a 1000 expense split 3 ways
 * would show up as 3000). This is deliberately different from
 * {@link BalanceService}, which is all about who owes what and therefore must
 * work from the splits.
 */
@Service
public class AnalyticsService {

    private static final String UNCATEGORIZED = "Uncategorized";
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ExpenseRepository expenseRepository;
    private final GroupAccessGuard groupAccessGuard;

    public AnalyticsService(ExpenseRepository expenseRepository, GroupAccessGuard groupAccessGuard) {
        this.expenseRepository = expenseRepository;
        this.groupAccessGuard = groupAccessGuard;
    }

    /** Category name -> total amount spent in that category for the group. */
    public Map<String, BigDecimal> spendByCategory(Long groupId, Long requestingUserId) {
        groupAccessGuard.requireMember(groupId, requestingUserId);

        Map<String, BigDecimal> totals = new TreeMap<>();
        for (Expense expense : expensesFor(groupId)) {
            String category = expense.getCategory();
            if (category == null || category.isBlank()) {
                category = UNCATEGORIZED;
            }
            totals.merge(category, expense.getAmount(), BigDecimal::add);
        }
        return totals;
    }

    /** Year-month ("2026-08") -> total amount spent that month for the group. */
    public Map<String, BigDecimal> spendByMonth(Long groupId, Long requestingUserId) {
        groupAccessGuard.requireMember(groupId, requestingUserId);

        Map<String, BigDecimal> totals = new TreeMap<>();
        for (Expense expense : expensesFor(groupId)) {
            if (expense.getExpenseDate() == null) {
                continue;
            }
            String yearMonth = expense.getExpenseDate().format(YEAR_MONTH);
            totals.merge(yearMonth, expense.getAmount(), BigDecimal::add);
        }
        return totals;
    }

    private List<Expense> expensesFor(Long groupId) {
        return expenseRepository.findByGroupIdOrderByExpenseDateDesc(groupId);
    }
}
