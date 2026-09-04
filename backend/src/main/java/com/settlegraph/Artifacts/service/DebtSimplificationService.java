package com.settlegraph.Artifacts.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * The centerpiece algorithm of SettleGraph.
 *
 * Problem: given each member's net balance in a group (positive = they are
 * owed money, negative = they owe money), find the SMALLEST possible set of
 * payments that settles everyone up.
 *
 * Approach: a greedy algorithm using two priority queues (max-heaps) — one
 * for creditors (owed money), one for debtors (owe money). On each step, we
 * match the largest creditor with the largest debtor, settle the smaller of
 * the two amounts between them, and push whichever side still has a
 * remaining balance back into its heap. Repeat until everyone is at zero.
 *
 * This is the same class of algorithm used by real expense-splitting apps —
 * it does not guarantee the mathematically optimal minimum in every possible
 * case (that general problem is NP-hard), but it performs very well in
 * practice and is simple enough to reason about, test, and explain.
 */
@Service
public class DebtSimplificationService {

    public record Payment(Long fromUserId, Long toUserId, BigDecimal amount) {}

    public List<Payment> simplify(Map<Long, BigDecimal> netBalances) {
        // Max-heap of creditors (people owed money), ordered by amount owed to them, descending.
        PriorityQueue<Map.Entry<Long, BigDecimal>> creditors =
                new PriorityQueue<>((a, b) -> b.getValue().compareTo(a.getValue()));
        // Max-heap of debtors (people who owe money), ordered by amount owed, descending.
        PriorityQueue<Map.Entry<Long, BigDecimal>> debtors =
                new PriorityQueue<>((a, b) -> b.getValue().compareTo(a.getValue()));

        for (Map.Entry<Long, BigDecimal> entry : netBalances.entrySet()) {
            int cmp = entry.getValue().compareTo(BigDecimal.ZERO);
            if (cmp > 0) {
                creditors.add(Map.entry(entry.getKey(), entry.getValue()));
            } else if (cmp < 0) {
                debtors.add(Map.entry(entry.getKey(), entry.getValue().abs()));
            }
            // cmp == 0 means already settled — nothing to do.
        }

        List<Payment> payments = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            var creditor = creditors.poll();
            var debtor = debtors.poll();

            BigDecimal settledAmount = creditor.getValue().min(debtor.getValue());
            payments.add(new Payment(debtor.getKey(), creditor.getKey(), settledAmount));

            BigDecimal creditorRemaining = creditor.getValue().subtract(settledAmount);
            BigDecimal debtorRemaining = debtor.getValue().subtract(settledAmount);

            if (creditorRemaining.compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(Map.entry(creditor.getKey(), creditorRemaining));
            }
            if (debtorRemaining.compareTo(BigDecimal.ZERO) > 0) {
                debtors.add(Map.entry(debtor.getKey(), debtorRemaining));
            }
        }

        return payments;
    }
}
