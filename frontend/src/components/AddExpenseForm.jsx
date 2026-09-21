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
      if (splitType === "EXACT" && Math.abs(splitTotal() - numericAmount) > 0.02) {
        setError(`Exact amounts (${splitTotal().toFixed(2)}) must add up to the total (${numericAmount.toFixed(2)})`);
        return;
      }
      if (splitType === "PERCENTAGE" && Math.abs(splitTotal() - 100) > 0.02) {
        setError(`Percentages must add up to 100 (currently ${splitTotal().toFixed(2)})`);
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
        </div>
      )}

      <button type="submit">Add expense</button>
      {error && <p className="error">{error}</p>}
    </form>
  );
}
