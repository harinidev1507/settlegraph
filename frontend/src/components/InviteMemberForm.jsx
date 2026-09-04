import { useEffect, useState } from "react";
import { searchUsers } from "../api/users";
import { inviteToGroup } from "../api/invites";

export default function InviteMemberForm({ groupId, onInvited }) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState([]);
  const [error, setError] = useState("");
  const [status, setStatus] = useState("");

  useEffect(() => {
    if (query.trim().length < 2) {
      setResults([]);
      return;
    }
    const timeout = setTimeout(async () => {
      try {
        const data = await searchUsers(query.trim());
        setResults(data);
      } catch {
        setResults([]);
      }
    }, 300);
    return () => clearTimeout(timeout);
  }, [query]);

  async function handleInvite(username) {
    setError("");
    setStatus("");
    try {
      await inviteToGroup(groupId, username);
      setStatus(`Invite sent to @${username}.`);
      setQuery("");
      setResults([]);
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
