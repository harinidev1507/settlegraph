import { useEffect, useState } from "react";
import { searchUsers } from "../api/users";
import { inviteToGroup } from "../api/invites";

export default function InviteMemberForm({ groupId, onInvited }) {
  const [query, setQuery] = useState("");
  // Results are tagged with the query they answer, and only shown while that
  // is still the current query: a slow response for "bo" can't overwrite the
  // results for "bob", and clearing the box hides results without an effect
  // having to reset state.
  const [found, setFound] = useState({ query: "", results: [] });
  const [error, setError] = useState("");
  const [status, setStatus] = useState("");

  const trimmed = query.trim();
  const results = trimmed.length >= 2 && found.query === trimmed ? found.results : [];

  useEffect(() => {
    if (trimmed.length < 2) return;
    const timeout = setTimeout(async () => {
      try {
        setFound({ query: trimmed, results: await searchUsers(trimmed) });
      } catch {
        setFound({ query: trimmed, results: [] });
      }
    }, 300);
    return () => clearTimeout(timeout);
  }, [trimmed]);

  async function handleInvite(username) {
    setError("");
    setStatus("");
    try {
      await inviteToGroup(groupId, username);
      setStatus(`Invite sent to @${username}.`);
      setQuery("");
      onInvited();
    } catch (err) {
      setError(err.response?.data?.error || "Could not send invite");
    }
  }

  return (
    <div className="invite-form">
      <input
        placeholder="Search by username"
        value={query}
        onChange={(e) => { setQuery(e.target.value); setStatus(""); setError(""); }}
      />
      {results.length > 0 && (
        <ul className="search-results">
          {results.map((r) => (
            <li key={r.userId}>
              <span>{r.name} <span className="muted">@{r.username}</span></span>
              <button className="btn-small" type="button" onClick={() => handleInvite(r.username)}>Invite</button>
            </li>
          ))}
        </ul>
      )}
      {status && <p className="success">{status}</p>}
      {error && <p className="error">{error}</p>}
    </div>
  );
}
