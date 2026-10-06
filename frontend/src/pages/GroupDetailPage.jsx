import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { getBalances, getGroup, getGroupMembers } from "../api/groups";
import { getExpensesForGroup, stopRecurring } from "../api/expenses";
import { generateSettlementPlan, getSettlements, markSettlementPaid } from "../api/settlements";
import { getGroupInvites } from "../api/invites";
import { apiErrorMessage } from "../api/errors";
import AddExpenseForm from "../components/AddExpenseForm";
import InviteMemberForm from "../components/InviteMemberForm";
import AuditLogSection from "../components/AuditLogSection";
import AnalyticsSection from "../components/AnalyticsSection";

// What to tell the user when the page itself can't load. A 403 here means
// "not a member" (the backend returns it for a group that doesn't exist too,
// so it can't be probed). A 401 never reaches this screen for long: the API
// client clears the session and redirects to /login.
function loadFailureMessage(err) {
  if (err?.response?.status === 403) return "You don't have access to this group.";
  return apiErrorMessage(err, "Could not load this group.");
}

export default function GroupDetailPage() {
  const { groupId } = useParams();
  // The last completed load, tagged with the group it belongs to, so a slow
  // response for a group we've navigated away from is never shown. Loading is
  // derived (no result for this group yet) rather than set inside the effect.
  const [result, setResult] = useState(null); // { groupId, data, reloadError? } | { groupId, error }
  // Bumped after every change on the page; reloads the data and tells the
  // (lazy) analytics section to refetch.
  const [reloadKey, setReloadKey] = useState(0);
  const [expenseError, setExpenseError] = useState("");
  const [settleError, setSettleError] = useState("");
  const [settleBusy, setSettleBusy] = useState(false);

  useEffect(() => {
    let ignore = false;
    Promise.all([
      getGroup(groupId),
      getBalances(groupId),
      getExpensesForGroup(groupId),
      getSettlements(groupId),
      getGroupMembers(groupId),
      getGroupInvites(groupId),
    ]).then(
      ([group, balances, expenses, settlements, members, groupInvites]) => {
        if (!ignore) setResult({ groupId, data: { group, balances, expenses, settlements, members, groupInvites } });
      },
      (err) => {
        if (ignore) return;
        // A failed refresh of a page that already loaded keeps the data on
        // screen with a banner; only a failed first load replaces the page.
        setResult((prev) => (prev?.groupId === groupId && prev.data
          ? { ...prev, reloadError: loadFailureMessage(err) }
          : { groupId, error: loadFailureMessage(err) }));
      }
    );
    return () => { ignore = true; };
  }, [groupId, reloadKey]);

  function reload() {
    setReloadKey((k) => k + 1);
  }

  const current = result?.groupId === groupId ? result : null;

  if (!current || current.error) {
    return (
      <div className="page">
        <Link to="/dashboard" className="back-link">&larr; Back to Dashboard</Link>
        {current?.error
          ? <p className="error">{current.error}</p>
          : <p className="muted">Loading…</p>}
      </div>
    );
  }

  const { group, balances, expenses, settlements, members, groupInvites } = current.data;

  function memberName(userId) {
    const member = members.find((m) => String(m.userId) === String(userId));
    return member ? member.name : `User #${userId}`;
  }

  // Settle-up actions are disabled while one is in flight: a double click on
  // "Mark paid" would otherwise send a second request that the backend
  // (correctly) rejects as already paid, and show that as an error.
  async function runSettleAction(action, fallbackMessage) {
    setSettleError("");
    setSettleBusy(true);
    try {
      await action();
      reload();
    } catch (err) {
      setSettleError(apiErrorMessage(err, fallbackMessage));
    } finally {
      setSettleBusy(false);
    }
  }

  function handleSettleUp() {
    runSettleAction(() => generateSettlementPlan(groupId), "Could not generate a settlement plan");
  }

  function handleMarkPaid(settlementId) {
    runSettleAction(() => markSettlementPaid(groupId, settlementId), "Could not mark the payment as paid");
  }

  async function handleStopRecurring(expenseId) {
    setExpenseError("");
    try {
      await stopRecurring(expenseId);
      reload();
    } catch (err) {
      setExpenseError(apiErrorMessage(err, "Could not stop the recurring expense"));
    }
  }

  return (
    <div className="page">
      <Link to="/dashboard" className="back-link">&larr; Back to Dashboard</Link>
      <div className="brand brand-inline">
        <span className="eyebrow">Group overview</span>
        <h1>{group.name}</h1>
      </div>
      {current.reloadError && <p className="error">{current.reloadError}</p>}

      <section>
        <span className="eyebrow">Who's in</span>
        <h2>Members</h2>
        <ul className="member-list">
          {members.map((m) => (
            <li key={m.userId}>
              <span>{m.name}</span>
              <span className="muted">@{m.username}</span>
            </li>
          ))}
        </ul>
        <InviteMemberForm groupId={groupId} onInvited={reload} />
        {groupInvites.filter((i) => i.status === "PENDING").length > 0 && (
          <div className="pending-invites">
            <p className="field-label">Pending invites</p>
            <ul className="invite-list">
              {groupInvites.filter((i) => i.status === "PENDING").map((inv) => (
                <li key={inv.id}>
                  <span>{inv.invitedName} <span className="muted">@{inv.invitedUsername}</span></span>
                  <span className="status-badge pending">WAITING</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </section>

      <section>
        <span className="eyebrow">Who owes what</span>
        <h2>Balances</h2>
        <ul className="balance-list">
          {Object.entries(balances).map(([userId, amt]) => (
            <li key={userId}>
              <span>{memberName(userId)}</span>
              <span className={`pill ${amt >= 0 ? "owed" : "owes"}`}>
                {amt >= 0 ? `is owed ${amt}` : `owes ${Math.abs(amt)}`}
              </span>
            </li>
          ))}
          {Object.keys(balances).length === 0 && <li className="muted">Everyone's settled up.</li>}
        </ul>
      </section>

      <section>
        <span className="eyebrow">Log a cost</span>
        <h2>Add an expense</h2>
        {members.length > 0 && (
          <AddExpenseForm groupId={groupId} members={members} onAdded={reload} />
        )}
      </section>

      <section>
        <span className="eyebrow">History</span>
        <h2>Expenses</h2>
        {expenseError && <p className="error">{expenseError}</p>}
        <ul className="expense-list">
          {expenses.map((e) => (
            <li key={e.id}>
              <span className="expense-main">
                <span>{e.description}</span>
                <span className="muted expense-meta">
                  {e.category || "Uncategorized"} · paid by {memberName(e.paidBy)}
                  {e.recurringSourceId && " · auto-added"}
                </span>
              </span>
              {e.recurring && <span className="status-badge recurring">MONTHLY</span>}
              <span className="amount">{e.amount} {e.currency}</span>
              {e.recurring && (
                <button className="btn-ghost btn-small" onClick={() => handleStopRecurring(e.id)}>
                  Stop repeating
                </button>
              )}
            </li>
          ))}
          {expenses.length === 0 && <li className="muted">No expenses logged yet.</li>}
        </ul>
      </section>

      <AnalyticsSection groupId={groupId} refreshKey={reloadKey} />

      <section>
        <span className="eyebrow">Wrap it up</span>
        <h2>Settle Up</h2>
        <button onClick={handleSettleUp} disabled={settleBusy}>Simplify & Generate Settlement Plan</button>
        {settleError && <p className="error">{settleError}</p>}
        <ul className="settlement-list">
          {settlements.map((s) => (
            <li key={s.id}>
              <span>{memberName(s.fromUserId)} pays {memberName(s.toUserId)}</span>
              <span className="amount">{s.amount}</span>
              <span className={`status-badge ${s.status.toLowerCase()}`}>{s.status}</span>
              {s.status === "PENDING" && (
                <button className="btn-ghost btn-small" disabled={settleBusy}
                        onClick={() => handleMarkPaid(s.id)}>Mark paid</button>
              )}
            </li>
          ))}
          {settlements.length === 0 && <li className="muted">No settlement plan generated yet.</li>}
        </ul>
      </section>

      <AuditLogSection groupId={groupId} nameFor={memberName} />
    </div>
  );
}
