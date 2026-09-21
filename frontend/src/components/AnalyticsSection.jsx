import { useEffect, useState } from "react";
import { getSpendByCategory, getSpendByMonth } from "../api/analytics";
import { apiErrorMessage } from "../api/errors";

// "2026-09" -> "Sep 2026". Falls back to the raw key if it isn't yyyy-MM.
function formatMonth(key) {
  const [year, month] = key.split("-").map(Number);
  if (!year || !month) return key;
  return new Date(year, month - 1, 1).toLocaleString(undefined, { month: "short", year: "numeric" });
}

function formatAmount(n) {
  return Number(n).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// One single-hue horizontal bar list: bar length = share of the section's total.
// Numbers stay in text ink; the bar only carries magnitude.
function BarList({ data, labelFor, emptyText }) {
  const rows = Object.entries(data).sort(([, a], [, b]) => Number(b) - Number(a));
  if (rows.length === 0) return <p className="muted">{emptyText}</p>;

  const total = rows.reduce((sum, [, v]) => sum + Number(v), 0);
  const max = Math.max(...rows.map(([, v]) => Number(v)));

  return (
    <ul className="bar-list">
      {rows.map(([key, value]) => {
        const amount = Number(value);
        const share = total > 0 ? (amount / total) * 100 : 0;
        return (
          <li key={key} title={`${labelFor(key)}: ${formatAmount(amount)} (${share.toFixed(0)}%)`}>
            <span className="bar-label">{labelFor(key)}</span>
            <span className="bar-track">
              <span className="bar-fill" style={{ width: `${max > 0 ? (amount / max) * 100 : 0}%` }} />
            </span>
            <span className="bar-value">
              {formatAmount(amount)} <span className="muted">{share.toFixed(0)}%</span>
            </span>
          </li>
        );
      })}
    </ul>
  );
}

export default function AnalyticsSection({ groupId, refreshKey }) {
  const [open, setOpen] = useState(false);
  const [byCategory, setByCategory] = useState({});
  const [byMonth, setByMonth] = useState({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function load() {
    setLoading(true);
    setError("");
    try {
      const [c, m] = await Promise.all([getSpendByCategory(groupId), getSpendByMonth(groupId)]);
      setByCategory(c);
      setByMonth(m);
    } catch (err) {
      setError(apiErrorMessage(err, "Could not load spending analytics"));
    } finally {
      setLoading(false);
    }
  }

  function toggle() {
    setOpen((v) => !v);
  }

  // Lazy-load on first expand (keeps it out of the page's initial burst of
  // requests) and refetch whenever the parent signals the expense list
  // changed, so the numbers don't go stale behind the user's back.
  useEffect(() => {
    if (open) load();
  }, [open, refreshKey]);

  return (
    <section className="analytics">
      <span className="eyebrow">Where it went</span>
      <button type="button" className="audit-toggle" onClick={toggle} aria-expanded={open}>
        <h2>Spending breakdown</h2>
        <span className={`chevron ${open ? "chevron-open" : ""}`}>&rsaquo;</span>
      </button>

      {open && (
        <div className="audit-log-body">
          {loading && <p className="muted">Crunching the numbers…</p>}
          {error && <p className="error">{error}</p>}
          {!loading && !error && (
            <div className="analytics-grid">
              <div>
                <p className="field-label">By category</p>
                <BarList data={byCategory} labelFor={(k) => k} emptyText="No expenses yet." />
              </div>
              <div>
                <p className="field-label">By month</p>
                <BarList data={byMonth} labelFor={formatMonth} emptyText="No expenses yet." />
              </div>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
