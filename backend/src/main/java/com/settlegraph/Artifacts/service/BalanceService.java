package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.entity.Expense;
import com.settlegraph.Artifacts.entity.ExpenseSplit;
import com.settlegraph.Artifacts.entity.Settlement;
import com.settlegraph.Artifacts.repository.ExpenseRepository;
import com.settlegraph.Artifacts.repository.ExpenseSplitRepository;
import com.settlegraph.Artifacts.repository.SettlementRepository;
import com.settlegraph.Artifacts.security.GroupAccessGuard;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes each member's net balance within a group:
 *   positive balance = this person is OWED money overall
 *   negative balance = this person OWES money overall
 *
 * Logic: whoever paid an expense is credited the full amount; everyone
 * assigned a share of that expense (including the payer, if they have a
 * share) is debited their share. Then any settlement that has actually been
 * PAID is netted out: the cash moved for real, so it cancels that much of the
 * debt. Net balance = expenses credited - expenses debited - settlements paid.
 */
@Service
public class BalanceService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final SettlementRepository settlementRepository;
    private final GroupAccessGuard groupAccessGuard;

    public BalanceService(ExpenseRepository expenseRepository,
                          ExpenseSplitRepository expenseSplitRepository,
                          SettlementRepository settlementRepository,
                          GroupAccessGuard groupAccessGuard) {
        this.expenseRepository = expenseRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.settlementRepository = settlementRepository;
        this.groupAccessGuard = groupAccessGuard;
    }

    /** Membership-checked entry point for the balances endpoint. */
    public Map<Long, BigDecimal> getNetBalances(Long groupId, Long requestingUserId) {
        groupAccessGuard.requireMember(groupId, requestingUserId);
        return calculateNetBalances(groupId);
    }

    public Map<Long, BigDecimal> calculateNetBalances(Long groupId) {
        Map<Long, BigDecimal> balances = new HashMap<>();
        List<Expense> expenses = expenseRepository.findByGroupIdOrderByExpenseDateDesc(groupId);

        for (Expense expense : expenses) {
            balances.merge(expense.getPaidBy(), expense.getAmount(), BigDecimal::add);

            List<ExpenseSplit> splits = expenseSplitRepository.findByIdExpenseId(expense.getId());
            for (ExpenseSplit split : splits) {
                balances.merge(split.getId().getUserId(), split.getShareAmount().negate(), BigDecimal::add);
            }
        }

        // Net out settlements that have actually been paid. A completed payment
        // of `amount` from payer -> receiver means real cash changed hands:
        //   payer had a negative (owes) balance -> add `amount`, moving toward 0
        //   receiver had a positive (owed) balance -> subtract `amount`, moving toward 0
        // PENDING settlements are only a plan; they don't move anything yet.
        for (Settlement settlement : settlementRepository.findByGroupId(groupId)) {
            if (settlement.getStatus() == Settlement.Status.PAID) {
                balances.merge(settlement.getFromUserId(), settlement.getAmount(), BigDecimal::add);
                balances.merge(settlement.getToUserId(), settlement.getAmount().negate(), BigDecimal::add);
            }
        }

        return balances;
    }
}
