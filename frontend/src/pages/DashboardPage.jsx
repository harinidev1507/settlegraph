import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getMyGroups, createGroup } from "../api/groups";
import { getMyInvites, acceptInvite, declineInvite } from "../api/invites";
import { apiErrorMessage } from "../api/errors";
import { useAuth } from "../context/useAuth";
import NotificationsSection from "../components/NotificationsSection";

export default function DashboardPage() {
  // The last completed load; null until the first one finishes. Loading is
  // derived from that rather than set inside the effect.
  const [result, setResult] = useState(null); // { groups?, invites?, error? }
  const [reloadKey, setReloadKey] = useState(0);
  const [newGroupName, setNewGroupName] = useState("");
  const [createError, setCreateError] = useState("");
  const [inviteError, setInviteError] = useState("");
  const { user, logout } = useAuth();

  useEffect(() => {
    let ignore = false;
    Promise.all([getMyGroups(), getMyInvites()]).then(
      ([groups, invites]) => { if (!ignore) setResult({ groups, invites }); },
      // A failed refresh keeps what's already on screen and adds the error.
      (err) => {
        if (!ignore) setResult((prev) => ({ ...prev, error: apiErrorMessage(err, "Could not load your groups and invites") }));
      }
    );
    return () => { ignore = true; };
  }, [reloadKey]);

  function reload() {
    setReloadKey((k) => k + 1);
  }

  async function handleCreateGroup(e) {
    e.preventDefault();
    if (!newGroupName.trim()) return;
    setCreateError("");
    try {
      await createGroup({ name: newGroupName });
      setNewGroupName("");
      reload();
    } catch (err) {
      setCreateError(apiErrorMessage(err, "Could not create the group"));
    }
  }

  async function respond(action, inviteId, fallbackMessage) {
    setInviteError("");
    try {
      await action(inviteId);
      reload();
    } catch (err) {
      setInviteError(apiErrorMessage(err, fallbackMessage));
    }
  }

  const loading = result === null;
  const loaded = Boolean(result?.groups); // at least one successful load
  const groups = result?.groups ?? [];
  const invites = result?.invites ?? [];

  return (
    <div className="page">
      <div className="topbar">
        <div className="brand brand-inline">
          <span className="eyebrow">SettleGraph</span>
          <h1>Hi, {user?.name}</h1>
        </div>
        <button className="btn-ghost" onClick={logout}>Log out</button>
      </div>

      <NotificationsSection />

      {result?.error && <p className="error">{result.error}</p>}

      <section>
        <span className="eyebrow">Waiting on you</span>
        <h2>Group invites</h2>
        {inviteError && <p className="error">{inviteError}</p>}
        <ul className="invite-list">
          {invites.map((inv) => (
            <li key={inv.id}>
              <span><strong>{inv.invitedByName}</strong> invited you to <strong>{inv.groupName}</strong></span>
              <span className="invite-actions">
                <button className="btn-small"
                        onClick={() => respond(acceptInvite, inv.id, "Could not accept the invite")}>Accept</button>
                <button className="btn-ghost btn-small"
                        onClick={() => respond(declineInvite, inv.id, "Could not decline the invite")}>Decline</button>
              </span>
            </li>
          ))}
          {loaded && invites.length === 0 && <li className="muted">No pending invites.</li>}
        </ul>
      </section>

      <section>
        <span className="eyebrow">New trip, new tab</span>
        <h2>Start a group</h2>
        <form onSubmit={handleCreateGroup} className="inline-form">
          <input placeholder="New group name (e.g. Goa Trip)" value={newGroupName}
                 onChange={(e) => setNewGroupName(e.target.value)} />
          <button type="submit">Create group</button>
        </form>
        {createError && <p className="error">{createError}</p>}
      </section>

      <section>
        <span className="eyebrow">Your circles</span>
        <h2>Groups</h2>
        {loading ? <p className="muted">Loading your groups...</p> : !loaded ? null : (
          groups.length === 0 ? <p className="muted">No groups yet — create one above to get started.</p> : (
            <ul className="group-list">
              {groups.map((g) => (
                <li key={g.id}>
                  <Link to={`/groups/${g.id}`}>
                    <span>{g.name}</span>
                    <span className="chevron">&rarr;</span>
                  </Link>
                </li>
              ))}
            </ul>
          )
        )}
      </section>
    </div>
  );
}
