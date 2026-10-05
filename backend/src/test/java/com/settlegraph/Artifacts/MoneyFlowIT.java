package com.settlegraph.Artifacts;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole money flow against the real Postgres, every step a real HTTP
 * request that commits: create group -> add expense -> check the stored
 * splits -> generate settlements -> each debtor marks theirs paid -> every
 * balance is exactly zero, via the API AND recomputed straight from the rows.
 */
class MoneyFlowIT extends IntegrationTestSupport {

    private static void assertMoney(String expected, BigDecimal actual, String what) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), what + ": expected " + expected + " but was " + actual);
    }

    private JsonNode balances(TestUser asUser, Long groupId) throws Exception {
        return body(mvc.perform(get("/api/groups/" + groupId + "/balances")
                .header("Authorization", asUser.bearer())).andExpect(status().isOk()));
    }

    @Test
    void createGroup_addExpense_settle_markPaid_leavesEveryBalanceAtExactlyZero() throws Exception {
        // Registered in this order, so alice has the lowest user id.
        TestUser alice = register("alice");
        TestUser bob = register("bob");
        TestUser carol = register("carol");
        Long groupId = createGroup(alice, "Goa Trip");
        join(alice, groupId, bob);
        join(alice, groupId, carol);

        // 1. Alice pays 100.00, split equally three ways.
        Long expenseId = addEqualExpense(alice, groupId, "100.00", List.of(alice, bob, carol), false)
                .get("id").asLong();

        // 2. The stored splits: largest remainder, tie to the lowest user id, sum exactly 100.00.
        List<Map<String, Object>> splits = jdbc.queryForList(
                "SELECT user_id, share_amount FROM expense_split WHERE expense_id = ? ORDER BY user_id", expenseId);
        assertEquals(3, splits.size());
        assertEquals(alice.id(), ((Number) splits.get(0).get("user_id")).longValue());
        assertMoney("33.34", (BigDecimal) splits.get(0).get("share_amount"), "alice's share");
        assertMoney("33.33", (BigDecimal) splits.get(1).get("share_amount"), "bob's share");
        assertMoney("33.33", (BigDecimal) splits.get(2).get("share_amount"), "carol's share");
        assertMoney("100.00", jdbc.queryForObject(
                "SELECT sum(share_amount) FROM expense_split WHERE expense_id = ?", BigDecimal.class, expenseId),
                "stored split sum");

        // 3. Balances before settling: alice is owed 66.66, the others owe 33.33 each.
        JsonNode before = balances(alice, groupId);
        assertMoney("66.66", before.get(alice.id().toString()).decimalValue(), "alice before");
        assertMoney("-33.33", before.get(bob.id().toString()).decimalValue(), "bob before");
        assertMoney("-33.33", before.get(carol.id().toString()).decimalValue(), "carol before");

        // 4. Generate the plan: bob -> alice 33.33 and carol -> alice 33.33, both PENDING.
        JsonNode plan = body(mvc.perform(post("/api/groups/" + groupId + "/settlements/generate")
                .header("Authorization", alice.bearer())).andExpect(status().isOk()));
        assertEquals(2, plan.size());
        for (JsonNode settlement : plan) {
            assertEquals(alice.id(), settlement.get("toUserId").asLong());
            assertMoney("33.33", settlement.get("amount").decimalValue(), "settlement amount");
            assertEquals("PENDING", settlement.get("status").asText());
        }

        // 5. Each debtor marks their own settlement paid.
        Map<Long, TestUser> debtors = Map.of(bob.id(), bob, carol.id(), carol);
        for (JsonNode settlement : plan) {
            TestUser debtor = debtors.get(settlement.get("fromUserId").asLong());
            JsonNode paid = body(mvc.perform(patch("/api/groups/" + groupId + "/settlements/"
                            + settlement.get("id").asLong() + "/mark-paid")
                    .header("Authorization", debtor.bearer())).andExpect(status().isOk()));
            assertEquals("PAID", paid.get("status").asText());
        }
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM settlement WHERE group_id = ? AND status = 'PAID'", Long.class, groupId));

        // 6. Every balance is exactly zero — through the API...
        JsonNode after = balances(bob, groupId);
        assertEquals(3, after.size());
        for (TestUser member : List.of(alice, bob, carol)) {
            assertMoney("0", after.get(member.id().toString()).decimalValue(), member.username() + " after (API)");
        }

        // ...and recomputed from the raw rows: paid credited, shares debited, PAID settlements netted.
        List<Map<String, Object>> fromRows = jdbc.queryForList("""
                SELECT user_id, sum(v) AS balance FROM (
                    SELECT paid_by AS user_id, amount AS v FROM expense WHERE group_id = :g
                    UNION ALL SELECT s.user_id, -s.share_amount FROM expense_split s
                              JOIN expense e ON e.id = s.expense_id WHERE e.group_id = :g
                    UNION ALL SELECT from_user_id, amount FROM settlement WHERE group_id = :g AND status = 'PAID'
                    UNION ALL SELECT to_user_id, -amount FROM settlement WHERE group_id = :g AND status = 'PAID'
                ) moves GROUP BY user_id ORDER BY user_id""".replace(":g", groupId.toString()));
        assertEquals(3, fromRows.size());
        for (Map<String, Object> row : fromRows) {
            assertMoney("0", (BigDecimal) row.get("balance"), "user " + row.get("user_id") + " after (SQL)");
        }
    }
}
