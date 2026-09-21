import { useEffect, useState } from "react";
import { getMyNotifications, markNotificationRead } from "../api/notifications";
import { apiErrorMessage } from "../api/errors";

function formatTimestamp(raw) {
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return raw;
  return date.toLocaleString(undefined, {
    day: "numeric", month: "short", hour: "numeric", minute: "2-digit",
  });
}

export default function NotificationsSection() {
  const [notifications, setNotifications] = useState([]);
  const [showRead, setShowRead] = useState(false);
  const [error, setError] = useState("");

  async function load() {
    setError("");
    try {
      setNotifications(await getMyNotifications());
    } catch (err) {
      setError(apiErrorMessage(err, "Could not load notifications"));
    }
  }

  useEffect(() => { load(); }, []);

  async function handleMarkRead(id) {
    try {
      await markNotificationRead(id);
      // Flip locally rather than refetch — the backend only ever moves a row
      // from unread to read, so this can't drift from the server.
      setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
    } catch (err) {
      setError(apiErrorMessage(err, "Could not mark as read"));
    }
  }

  const unread = notifications.filter((n) => !n.read);
  const visible = showRead ? notifications : unread;

  return (
    <section className="notifications">
      <span className="eyebrow">What you missed</span>
      <div className="section-head">
        <h2>
          Notifications
          {unread.length > 0 && <span className="count-badge">{unread.length}</span>}
        </h2>
        {notifications.length > unread.length && (
          <button type="button" className="btn-ghost btn-small" onClick={() => setShowRead((v) => !v)}>
            {showRead ? "Hide read" : "Show read"}
          </button>
        )}
      </div>
      {error && <p className="error">{error}</p>}
      <ul className="notification-list">
        {visible.map((n) => (
          <li key={n.id} className={n.read ? "is-read" : "is-unread"}>
            <span className="notification-body">
              <span>{n.message}</span>
              <time className="audit-time" dateTime={n.createdAt}>{formatTimestamp(n.createdAt)}</time>
            </span>
            {!n.read && (
              <button type="button" className="btn-ghost btn-small" onClick={() => handleMarkRead(n.id)}>
                Mark read
              </button>
            )}
          </li>
        ))}
        {visible.length === 0 && !error && (
          <li className="muted">{showRead ? "No notifications yet." : "You're all caught up."}</li>
        )}
      </ul>
    </section>
  );
}
