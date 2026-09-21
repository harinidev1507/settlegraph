import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getMyGroups, createGroup } from "../api/groups";
import { getMyInvites, acceptInvite, declineInvite } from "../api/invites";
import { useAuth } from "../context/AuthContext";
import NotificationsSection from "../components/NotificationsSection";

export default function DashboardPage() {
  const [groups, setGroups] = useState([]);
  const [invites, setInvites] = useState([]);
  const [loading, setLoading] = useState(true);
  const [newGroupName, setNewGroupName] = useState("");
  const { user, logout } = useAuth();

  async function loadAll() {
    setLoading(true);
    const [groupData, inviteData] = await Promise.all([getMyGroups(), getMyInvites()]);
    setGroups(groupData);
    setInvites(inviteData);
    setLoading(false);
  }

  useEffect(() => { loadAll(); }, []);

  async function handleCreateGroup(e) {
    e.preventDefault();
    if (!newGroupName.trim()) return;
    await createGroup({ name: newGroupName, memberUserIds: [] });
    setNewGroupName("");
    loadAll();
  }

  async function handleAccept(inviteId) {
    await acceptInvite(inviteId);
    loadAll();
  }

  async function handleDecline(inviteId) {
    await declineInvite(inviteId);
    loadAll();
  }

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

      <section>
        <span className="eyebrow">Waiting on you</span>
        <h2>Group invites</h2>
        <ul className="invite-list">
          {invites.map((inv) => (
            <li key={inv.id}>
              <span><strong>{inv.invitedByName}</strong> invited you to <strong>{inv.groupName}</strong></span>
              <span className="invite-actions">
                <button className="btn-small" onClick={() => handleAccept(inv.id)}>Accept</button>
                <button className="btn-ghost btn-small" onClick={() => handleDecline(inv.id)}>Decline</button>
              </span>
            </li>
          ))}
          {invites.length === 0 && <li className="muted">No pending invites.</li>}
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
      </section>

      <section>
        <span className="eyebrow">Your circles</span>
        <h2>Groups</h2>
        {loading ? <p className="muted">Loading your groups...</p> : (
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
