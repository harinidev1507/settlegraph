import { useState } from "react";
import { getAuditLog } from "../api/auditLog";
import { apiErrorMessage } from "../api/errors";

// Turns the backend's ISO timestamp (LocalDateTime, no zone) into something
// readable like "29 Aug 2026, 2:32 PM". Falls back to the raw string if the
// browser can't parse it.
function formatTimestamp(raw) {
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return raw;
  return date.toLocaleString(undefined, {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

export default function AuditLogSection({ groupId }) {
  const [open, setOpen] = useState(false);
  const [entries, setEntries] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState("");

  async function load() {
    setLoading(true);
    setError("");
    try {
      const data = await getAuditLog(groupId);
      setEntries(data);
      setLoaded(true);
    } catch (err) {
      setError(apiErrorMessage(err, "Could not load the audit log"));
    } finally {
      setLoading(false);
    }
  }

  function toggle() {
    const next = !open;
    setOpen(next);
    // Lazy-load on first expand so this stays out of the page's initial burst
    // of requests. Re-fetch on every re-open so it reflects recent activity.
    if (next) load();
  }

  return (
    <section className="audit-log">
      <span className="eyebrow">Paper trail</span>
      <button type="button" className="audit-toggle" onClick={toggle} aria-expanded={open}>
        <h2>Activity log</h2>
        <span className={`chevron ${open ? "chevron-open" : ""}`}>&rsaquo;</span>
      </button>

      {open && (
        <div className="audit-log-body">
          {loading && <p className="muted">Loading activity…</p>}
          {error && <p className="error">{error}</p>}
          {!loading && !error && loaded && entries.length === 0 && (
            <p className="muted">Nothing has happened in this group yet.</p>
          )}
          {!loading && !error && entries.length > 0 && (
            <ul className="audit-log-list">
              {entries.map((entry) => (
                <li key={entry.id}>
                  <span className="audit-action">{entry.action}</span>
                  <time className="audit-time" dateTime={entry.createdAt}>
                    {formatTimestamp(entry.createdAt)}
                  </time>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  );
}
