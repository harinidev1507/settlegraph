import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { getBalances, getGroup, getGroupMembers } from "../api/groups";
import { getExpensesForGroup, stopRecurring } from "../api/expenses";
import { generateSettlementPlan, getSettlements, markSettlementPaid } from "../api/settlements";
import { getGroupInvites } from "../api/invites";
import AddExpenseForm from "../components/AddExpenseForm";
import InviteMemberForm from "../components/InviteMemberForm";
import AuditLogSection from "../components/AuditLogSection";
import AnalyticsSection from "../components/AnalyticsSection";

export default function GroupDetailPage() {
  const { groupId } = useParams();
  const [group, setGroup] = useState(null);
  const [balances, setBalances] = useState({});
  const [expenses, setExpenses] = useState([]);
  const [settlements, setSettlements] = useState([]);
  const [members, setMembers] = useState([]);
  const [groupInvites, setGroupInvites] = useState([]);
  const [error, setError] = useState("");
  // Bumped on every reload so the (lazy) analytics section knows to refetch.
  const [refreshKey, setRefreshKey] = useState(0);

  async function loadAll() {
    const [g, b, e, s, m, i] = await Promise.all([
      getGroup(groupId),
      getBalances(groupId),
      getExpensesForGroup(groupId),
      getSettlements(groupId),
      getGroupMembers(groupId),
      getGroupInvites(groupId),
    ]);
    setGroup(g);
    setBalances(b);
    setExpenses(e);
    setSettlements(s);
    setMembers(m);
    setGroupInvites(i);
    setRefreshKey((k) => k + 1);
  }

  useEffect(() => { loadAll(); }, [groupId]);

  function memberName(userId) {
    const member = members.find((m) => String(m.userId) === String(userId));
    return member ? member.name : `User #${userId}`;
  }

  async function handleSettleUp() {
    await generateSettlementPlan(groupId);
    loadAll();
  }

  async function handleMarkPaid(settlementId) {
    await markSettlementPaid(groupId, settlementId);
    loadAll();
  }

  async function handleStopRecurring(expenseId) {
    setError("");
    try {
      await stopRecurring(expenseId);
      loadAll();
    } catch (err) {
      setError(err.response?.data?.error || "Could not stop the recurring expense");
    }
  }

  return (
    <div className="page">
      <Link to="/dashboard" className="back-link">&larr; Back to Dashboard</Link>
      <div className="brand brand-inline">
        <span className="eyebrow">Group overview</span>
        <h1>{group ? group.name : "Loading…"}</h1>
      </div>

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
        <InviteMemberForm groupId={groupId} onInvited={loadAll} />
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
          <AddExpenseForm groupId={groupId} members={members} onAdded={loadAll} />
        )}
      </section>

      <section>
        <span className="eyebrow">History</span>
        <h2>Expenses</h2>
        {error && <p className="error">{error}</p>}
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

      <AnalyticsSection groupId={groupId} refreshKey={refreshKey} />

      <section>
        <span className="eyebrow">Wrap it up</span>
        <h2>Settle Up</h2>
        <button onClick={handleSettleUp}>Simplify & Generate Settlement Plan</button>
        <ul className="settlement-list">
          {settlements.map((s) => (
            <li key={s.id}>
              <span>{memberName(s.fromUserId)} pays {memberName(s.toUserId)}</span>
              <span className="amount">{s.amount}</span>
              <span className={`status-badge ${s.status.toLowerCase()}`}>{s.status}</span>
              {s.status === "PENDING" && (
                <button className="btn-ghost btn-small" onClick={() => handleMarkPaid(s.id)}>Mark paid</button>
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
