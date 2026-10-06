import { describe, it, expect } from "vitest";
import { previewShares } from "./sharesPreview";

// The SHARES preview mirrors ExpenseService.allocate() on the backend. Cases
// are labelled by origin: [backend] cases are ported from ExpenseServiceTest
// (same inputs, same expected cents), so a drift between the two copies of the
// rule fails here; [click-through] cases are real rows the backend stored on
// 2026-10-06; [frontend] cases cover preview-only behaviour.

const member = (userId, name) => ({ userId, name: name ?? `User ${userId}` });

// previewShares returns rows in member order; key them by user ID for asserts.
function amountsByUser(amount, members, splitValues) {
  const rows = previewShares(amount, members, splitValues);
  return rows && Object.fromEntries(rows.map((r) => [r.userId, r.amount]));
}

describe("previewShares — ported from the backend's ExpenseServiceTest", () => {
  it("[backend] 10.00 at 1:2 gives the leftover cent to the largest remainder, not the lowest user ID", () => {
    // sharesSplit_1to2_leftoverCentGoesToTheLargestRemainder_notTheLowestUserId
    expect(amountsByUser("10.00", [member(1), member(2)], { 1: "1", 2: "2" }))
      .toEqual({ 1: "3.33", 2: "6.67" });
  });

  it("[backend] 100.00 at 1:1:1 with members out of order gives the tied cent to the lowest user ID", () => {
    // equalSplit_100Among3_sumsExactlyToTotal_tiedRemaindersGoToLowestUserId
    // (EQUAL goes through the same allocate() with weight 1 each).
    const members = [member(3), member(1), member(2)]; // deliberately unsorted
    expect(amountsByUser("100.00", members, { 1: "1", 2: "1", 3: "1" }))
      .toEqual({ 1: "33.34", 2: "33.33", 3: "33.33" });
  });

  it("[backend] 100.00 among 7 gives the 4 leftover cents to user IDs 1-4", () => {
    // equalSplit_100Among7_isAccepted_andSumsExactlyToTotal
    const members = [1, 2, 3, 4, 5, 6, 7].map((id) => member(id));
    const values = Object.fromEntries(members.map((m) => [m.userId, "1"]));
    expect(amountsByUser("100.00", members, values)).toEqual({
      1: "14.29", 2: "14.29", 3: "14.29", 4: "14.29", 5: "14.28", 6: "14.28", 7: "14.28",
    });
  });

  it("[backend] decimal weights 33.33 / 33.33 / 33.34 on 10.00 give the cent to user 3, the largest remainder", () => {
    // percentageSplit_leftoverCentGoesToTheLargestRemainder_notTheLowestUserId
    // (the backend runs PERCENTAGE through the same allocate() as SHARES).
    expect(amountsByUser("10.00", [member(1), member(2), member(3)], { 1: "33.33", 2: "33.33", 3: "33.34" }))
      .toEqual({ 1: "3.33", 2: "3.33", 3: "3.34" });
  });

  it("[backend] across many amounts and share mixes, shares sum exactly to the total and are each within a cent of exact", () => {
    // Same property as allocatedSplits_acrossManyAmountsAndParticipantCounts_... :
    // right total AND right per person, so a right-total-wrong-person bug fails.
    const amounts = ["0.01", "0.02", "0.99", "1.00", "10.00", "33.33", "99.99", "100.00", "100.01", "12345.67"];
    let checked = 0;
    for (const amount of amounts) {
      for (let n = 1; n <= 7; n++) {
        const members = Array.from({ length: n }, (_, i) => member(i + 1));
        // Weights 1..n in a fixed pattern (no randomness, so a failure reproduces).
        const values = Object.fromEntries(members.map((m, i) => [m.userId, String(((i * 3) % 5) + 1)]));
        const rows = previewShares(amount, members, values);
        const totalCents = Math.round(Number(amount) * 100);
        const weightSum = Object.values(values).reduce((s, v) => s + Number(v), 0);

        expect(rows.reduce((s, r) => s + Math.round(Number(r.amount) * 100), 0)).toBe(totalCents);
        for (const r of rows) {
          const exactCents = (totalCents * Number(values[r.userId])) / weightSum;
          expect(Math.abs(Math.round(Number(r.amount) * 100) - exactCents)).toBeLessThan(1);
        }
        checked++;
      }
    }
    expect(checked).toBe(70); // guard: the loops really ran
  });
});

describe("previewShares — cases from the 2026-10-06 click-through", () => {
  it("[click-through] 100.01 at 1:1 gives the tied cent to the lower user ID (Ana, 38), whatever the member order", () => {
    const members = [member(39, "Ben"), member(38, "Ana")];
    expect(amountsByUser("100.01", members, { 38: "1", 39: "1" })).toEqual({ 38: "50.01", 39: "50.00" });
  });

  it("[click-through] 75 at 11:21 matches the row the backend stored: 25.78 / 49.22", () => {
    expect(amountsByUser("75", [member(38), member(39)], { 38: "11", 39: "21" })).toEqual({ 38: "25.78", 39: "49.22" });
  });
});

describe("previewShares — preview-only behaviour", () => {
  it("[frontend] decimal shares: 10 at 1.5:1 gives 6.00 / 4.00", () => {
    expect(amountsByUser("10", [member(1), member(2)], { 1: "1.5", 2: "1" })).toEqual({ 1: "6.00", 2: "4.00" });
  });

  it("[frontend] shares with different decimal places are compared exactly: 9 at 0.5:2.25 gives 1.64 / 7.36", () => {
    // 0.5 : 2.25 = 2 : 9 -> exact 1.6363... / 7.3636..., floors 1.63 + 7.36 = 8.99.
    // User 1's remainder (0.63 of a cent) beats user 2's (0.36), so user 1 gets
    // the cent — the weights must be brought to a common scale to see that.
    expect(amountsByUser("9", [member(1), member(2)], { 1: "0.5", 2: "2.25" })).toEqual({ 1: "1.64", 2: "7.36" });
  });

  it("[frontend] an amount with more than two decimal places shows no preview rather than a guess", () => {
    expect(previewShares("10.005", [member(1), member(2)], { 1: "1", 2: "1" })).toBeNull();
  });

  it("[frontend] no preview without a positive amount or at least one positive share", () => {
    const members = [member(1), member(2)];
    expect(previewShares("", members, { 1: "1", 2: "1" })).toBeNull();
    expect(previewShares("0", members, { 1: "1", 2: "1" })).toBeNull();
    expect(previewShares("abc", members, { 1: "1", 2: "1" })).toBeNull();
    expect(previewShares("10", members, { 1: "", 2: "0" })).toBeNull();
  });

  it("[frontend] members with a blank or zero share are left out, exactly as submit leaves them out", () => {
    const rows = previewShares("30", [member(1), member(2), member(3)], { 1: "1", 2: "", 3: "2" });
    expect(rows.map((r) => r.userId)).toEqual([1, 3]);
    expect(Object.fromEntries(rows.map((r) => [r.userId, r.amount]))).toEqual({ 1: "10.00", 3: "20.00" });
  });
});
