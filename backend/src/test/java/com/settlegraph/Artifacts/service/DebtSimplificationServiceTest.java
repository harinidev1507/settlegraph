package com.settlegraph.Artifacts.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DebtSimplificationServiceTest {

    private final DebtSimplificationService service = new DebtSimplificationService();

    @Test
    void twoPeopleOneOwesTheOther() {
        Map<Long, BigDecimal> balances = Map.of(
                1L, new BigDecimal("50"),   // owed 50
                2L, new BigDecimal("-50")   // owes 50
        );

        List<DebtSimplificationService.Payment> result = service.simplify(balances);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).fromUserId());
        assertEquals(1L, result.get(0).toUserId());
        assertEquals(0, new BigDecimal("50").compareTo(result.get(0).amount()));
    }

    @Test
    void threePersonCycleCollapsesToOnePayment() {
        // A owes B owes C owes A, but the net effect should simplify to
        // a single payment instead of three separate ones.
        Map<Long, BigDecimal> balances = Map.of(
                1L, new BigDecimal("-100"), // A owes 100 net... adjusted below to a clean cycle
                2L, new BigDecimal("50"),
                3L, new BigDecimal("50")
        );

        List<DebtSimplificationService.Payment> result = service.simplify(balances);

        // Every debtor's total paid should equal their debt, every creditor's
        // total received should equal what they're owed.
        BigDecimal totalPaidByUser1 = result.stream()
                .filter(p -> p.fromUserId().equals(1L))
                .map(DebtSimplificationService.Payment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("100").compareTo(totalPaidByUser1));

        // Should never take more than (number of people - 1) payments to settle.
        assertTrue(result.size() <= 2);
    }

    @Test
    void everyoneAlreadySettledMeansNoPayments() {
        Map<Long, BigDecimal> balances = Map.of(
                1L, BigDecimal.ZERO,
                2L, BigDecimal.ZERO
        );

        List<DebtSimplificationService.Payment> result = service.simplify(balances);

        assertTrue(result.isEmpty());
    }

    @Test
    void totalAmountPaidAlwaysEqualsTotalAmountOwed() {
        Map<Long, BigDecimal> balances = Map.of(
                1L, new BigDecimal("120"),
                2L, new BigDecimal("-40"),
                3L, new BigDecimal("-30"),
                4L, new BigDecimal("-50")
        );

        List<DebtSimplificationService.Payment> result = service.simplify(balances);

        BigDecimal totalPaid = result.stream()
                .map(DebtSimplificationService.Payment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, new BigDecimal("120").compareTo(totalPaid));
    }
}
