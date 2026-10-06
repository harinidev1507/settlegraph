import { useState } from "react";
import { addExpense } from "../api/expenses";

// Fixed list on purpose — free-text categories would fragment the
// analytics/by-category buckets ("food", "Food", "food " ...).
const CATEGORIES = ["Food", "Travel", "Rent", "Utilities", "Other"];

const SPLIT_TYPES = [
  { value: "EQUAL", label: "Equal" },
  { value: "EXACT", label: "Exact amounts" },
  { value: "PERCENTAGE", label: "Percentage" },
  { value: "SHARES", label: "Shares" },
];

function memberLabel(member) {
  return member.name;
}

// A plain decimal string as an exact integer plus its number of decimal places
// ("1.5" -> {int: 15n, scale: 1}), or null for anything else (blank, "1e3").
function parseDecimal(raw) {
  const s = String(raw ?? "").trim();
  if (!/^\d+(\.\d+)?$/.test(s) && !/^\.\d+$/.test(s)) return null;
  const [whole, frac = ""] = s.split(".");
  return { int: BigInt((whole || "0") + frac), scale: frac.length };
}

// What the backend will store for a SHARES split, to the cent: a mirror of
// ExpenseService.allocate(). Each share is floored to the cent, then the
// leftover cents go one at a time to the largest remainders, ties by ascending
// user ID. Exact integer arithmetic, so the preview can't disagree with the
// saved rows by a cent. Returns null when the inputs can't be previewed yet.
function previewShares(amount, members, splitValues) {
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
    name: r.member.name,
    amount: `${r.cents / 100n}.${String(r.cents % 100n).padStart(2, "0")}`,
  }));
}

export default function AddExpenseForm({ groupId, members, onAdded }) {
  const [description, setDescription] = useState("");
  const [amount, setAmount] = useState("");
  const [category, setCategory] = useState("Other");
  const [splitType, setSplitType] = useState("EQUAL");
  const [splitValues, setSplitValues] = useState({});
  const [recurring, setRecurring] = useState(false);
  const [error, setError] = useState("");

  function handleSplitTypeChange(value) {
    setSplitType(value);
    if (value === "SHARES") {
      const defaults = {};
      members.forEach((m) => { defaults[m.userId] = "1"; });
      setSplitValues(defaults);
    } else if (value === "EQUAL") {
      setSplitValues({});
    } else {
      setSplitValues({});
    }
  }

  function handleSplitValueChange(userId, value) {
    setSplitValues((prev) => ({ ...prev, [userId]: value }));
  }

  function splitTotal() {
    return Object.values(splitValues).reduce((sum, v) => sum + (Number(v) || 0), 0);
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");

    const numericAmount = Number(amount);
    if (!numericAmount || numericAmount <= 0) {
      setError("Enter a valid amount");
      return;
    }

    let splits;
    if (splitType !== "EQUAL") {
      splits = {};
      for (const m of members) {
        const raw = splitValues[m.userId];
        const value = Number(raw);
        if (raw !== undefined && raw !== "" && value > 0) {
          splits[m.userId] = value;
        }
      }
      if (Object.keys(splits).length === 0) {
        setError("Enter a split for at least one member");
        return;
      }
      // Exact amounts are stored to the cent, so a sub-cent value (33.335) would
      // be silently rounded on save. The backend rejects it; stop it here first.
      // The epsilon only absorbs float noise (0.29 * 100 = 28.999...), not real cents.
      if (splitType === "EXACT") {
        const subCent = members.find((m) => splits[m.userId] !== undefined
          && Math.abs(splits[m.userId] * 100 - Math.round(splits[m.userId] * 100)) > 1e-6);
        if (subCent) {
          setError(`Exact amount for ${subCent.name} can have at most 2 decimal places`);
          return;
        }
      }
      // Both must match exactly, as the backend requires. Compare in whole cents
      // (and with a float-noise epsilon for percentages) so 0.1 + 0.2 style
      // binary rounding can't cause a false rejection.
      if (splitType === "EXACT" && Math.round(splitTotal() * 100) !== Math.round(numericAmount * 100)) {
        setError(`Exact amounts (${splitTotal().toFixed(2)}) must add up to the total (${numericAmount.toFixed(2)})`);
        return;
      }
      if (splitType === "PERCENTAGE" && Math.abs(splitTotal() - 100) > 1e-9) {
        setError(`Percentages must add up to 100 (currently ${Number(splitTotal().toFixed(6))})`);
        return;
      }
    }

    try {
      await addExpense({
        groupId: Number(groupId),
        amount: numericAmount,
        currency: "INR",
        category,
        description,
        splitType,
        splits,
        recurring,
        // Backend rejects recurrenceFrequency when recurring is false, so only
        // send it when the box is ticked. MONTHLY is the only option today.
        recurrenceFrequency: recurring ? "MONTHLY" : undefined,
        // EQUAL split needs an explicit participant list — the backend no longer
        // infers it from current group membership. Default to everyone shown.
        participantIds: splitType === "EQUAL" ? members.map((m) => m.userId) : undefined,
      });
      setDescription("");
      setAmount("");
      setCategory("Other");
      setSplitType("EQUAL");
      setSplitValues({});
      setRecurring(false);
      onAdded();
    } catch (err) {
      setError(err.response?.data?.error || "Could not add expense");
    }
  }

  return (
    <form onSubmit={handleSubmit} className="add-expense-form">
      <div className="inline-form">
        <input placeholder="What was it for?" value={description}
               onChange={(e) => setDescription(e.target.value)} required />
        <input type="number" placeholder="Amount" value={amount}
               onChange={(e) => setAmount(e.target.value)} required />
        <select value={category} onChange={(e) => setCategory(e.target.value)} aria-label="Category">
          {CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
        </select>
        <select value={splitType} onChange={(e) => handleSplitTypeChange(e.target.value)} aria-label="Split type">
          {SPLIT_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
        </select>
      </div>

      <label className="checkbox-row">
        <input type="checkbox" checked={recurring} onChange={(e) => setRecurring(e.target.checked)} />
        <span>Repeats monthly <span className="muted">(same payer, amount and split, added automatically each month)</span></span>
      </label>

      {splitType !== "EQUAL" && (
        <div className="split-inputs">
          {members.map((m) => (
            <div className="split-row" key={m.userId}>
              <span>{memberLabel(m)}</span>
              <input
                type="number"
                placeholder={splitType === "SHARES" ? "Shares" : splitType === "PERCENTAGE" ? "%" : "Amount"}
                value={splitValues[m.userId] ?? ""}
                onChange={(e) => handleSplitValueChange(m.userId, e.target.value)}
              />
            </div>
          ))}
          {(splitType === "EXACT" || splitType === "PERCENTAGE") && (
            <p className="split-total">
              Total: {splitTotal().toFixed(2)}{splitType === "PERCENTAGE" ? "%" : ""}
              {" "}(target: {splitType === "PERCENTAGE" ? "100" : (Number(amount) || 0).toFixed(2)})
            </p>
          )}
          {splitType === "SHARES" && (() => {
            const preview = previewShares(amount, members, splitValues);
            return (
              <p className="split-total">
                {preview
                  ? <>Each pays: {preview.map((p) => `${p.name} ${p.amount}`).join(" · ")}</>
                  : "Enter an amount and shares to see what each person pays"}
              </p>
            );
          })()}
        </div>
      )}

      <button type="submit">Add expense</button>
      {error && <p className="error">{error}</p>}
    </form>
  );
}
