// What the backend will store for a SHARES split, to the cent. Kept as plain
// functions in their own module so the rule can be tested directly against the
// backend's own cases: it mirrors ExpenseService.allocate(), and any change
// there must be made here too.

// A plain decimal string as an exact integer plus its number of decimal places
// ("1.5" -> {int: 15n, scale: 1}), or null for anything else (blank, "1e3").
export function parseDecimal(raw) {
  const s = String(raw ?? "").trim();
  if (!/^\d+(\.\d+)?$/.test(s) && !/^\.\d+$/.test(s)) return null;
  const [whole, frac = ""] = s.split(".");
  return { int: BigInt((whole || "0") + frac), scale: frac.length };
}

// Each share is floored to the cent, then the leftover cents go one at a time
// to the largest remainders, ties by ascending user ID. Exact integer
// arithmetic, so the preview can't disagree with the saved rows by a cent.
// Returns [{userId, name, amount: "12.34"}] in member order, or null when the
// inputs can't be previewed yet.
export function previewShares(amount, members, splitValues) {
  const amt = parseDecimal(amount);
  if (!amt || amt.scale > 2 || amt.int === 0n) return null;
  const amountCents = amt.int * 10n ** BigInt(2 - amt.scale);

  // Same filter as submit: only members with a positive value take part.
  const weights = members
    .map((m) => ({ member: m, w: parseDecimal(splitValues[m.userId]) }))
    .filter((x) => x.w && x.w.int > 0n);
  if (weights.length === 0) return null;
  // Bring every weight to the same scale so they compare as integers.
  const scale = Math.max(...weights.map((x) => x.w.scale));
  const scaled = weights.map((x) => ({ ...x, w: x.w.int * 10n ** BigInt(scale - x.w.scale) }));
  const total = scaled.reduce((sum, x) => sum + x.w, 0n);

  const rows = scaled.map((x) => {
    const numerator = amountCents * x.w;
    const cents = numerator / total; // BigInt division floors for positives
    return { member: x.member, cents, remainder: numerator - cents * total };
  });
  let leftover = amountCents - rows.reduce((sum, r) => sum + r.cents, 0n);
  [...rows]
    .sort((a, b) => (b.remainder > a.remainder ? 1 : b.remainder < a.remainder ? -1
      : Number(a.member.userId) - Number(b.member.userId)))
    .forEach((r) => { if (leftover > 0n) { r.cents += 1n; leftover -= 1n; } });

  return rows.map((r) => ({
    userId: r.member.userId,
    name: r.member.name,
    amount: `${r.cents / 100n}.${String(r.cents % 100n).padStart(2, "0")}`,
  }));
}
