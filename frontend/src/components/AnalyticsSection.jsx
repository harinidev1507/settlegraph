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

// "2026-09" -> "2026-10". Plain string/number math: no Date object, so no
// timezone can shift a month boundary.
function nextMonthKey(key) {
  const [year, month] = key.split("-").map(Number);
  return month === 12 ? `${year + 1}-01` : `${year}-${String(month + 1).padStart(2, "0")}`;
}

// Every month from the earliest to the latest with spending, in calendar order,
// months with no expenses as 0. A gap is information: an empty month should
// look empty, not silently disappear. (yyyy-MM keys sort chronologically as text.)
function fillMonths(byMonth) {
  const keys = Object.keys(byMonth).sort();
  if (keys.length === 0) return [];
  const last = keys[keys.length - 1];
  const months = [];
  for (let key = keys[0]; key <= last && months.length < 600; key = nextMonthKey(key)) {
    months.push([key, Number(byMonth[key] ?? 0)]);
  }
  return months;
}

const compact = new Intl.NumberFormat(undefined, { notation: "compact", maximumFractionDigits: 1 });

function shortMonth(key) {
  const [year, month] = key.split("-").map(Number);
  return new Date(year, month - 1, 1).toLocaleString(undefined, { month: "short" });
}

// Vertical columns, left to right in time. Column height = share of the
// busiest month. The exact amount is in the tooltip and the accessible label;
// the visible label is compact so narrow columns don't overflow.
function MonthColumns({ data, emptyText }) {
  const months = fillMonths(data);
  if (months.length === 0) return <p className="muted">{emptyText}</p>;
  const max = Math.max(...months.map(([, amount]) => amount));

  return (
    <ol className="month-columns">
      {months.map(([key, amount], i) => {
        const showYear = i === 0 || key.endsWith("-01");
        const label = `${formatMonth(key)}: ${formatAmount(amount)}`;
        return (
          <li key={key} title={label} aria-label={label}>
            <span className="month-value">{amount > 0 ? compact.format(amount) : "–"}</span>
            <span className="month-track">
              <span className="month-fill" style={{ height: `${max > 0 ? (amount / max) * 100 : 0}%` }} />
            </span>
            <span className="month-label">
              {shortMonth(key)}
              {showYear && <span className="month-year">{key.slice(0, 4)}</span>}
            </span>
          </li>
        );
      })}
    </ol>
  );
}

// One category: a single 100% bar conveys nothing, so say it in a sentence.
function CategoryBreakdown({ data, emptyText }) {
  const entries = Object.entries(data);
  if (entries.length === 1) {
    const [category, amount] = entries[0];
    return (
      <p className="analytics-single">
        All spending so far is in one category, <strong>{category}</strong>: {formatAmount(amount)}.
      </p>
    );
  }
  return <BarList data={data} labelFor={(k) => k} emptyText={emptyText} />;
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
                <CategoryBreakdown data={byCategory} emptyText="No expenses yet." />
              </div>
              <div>
                <p className="field-label">By month</p>
                <MonthColumns data={byMonth} emptyText="No expenses yet." />
              </div>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
